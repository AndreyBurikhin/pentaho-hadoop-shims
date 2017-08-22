/*******************************************************************************
 *
 * Pentaho Big Data
 *
 * Copyright (C) 2017 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package org.pentaho.hadoop.shim.common.format;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.log4j.Logger;

//#if shim_type=="HDP" || shim_type=="EMR" || shim_type=="HDI" || shim_type=="MAPR"
import org.apache.parquet.hadoop.ParquetInputFormat;
import org.apache.parquet.hadoop.ParquetRecordReader;
//#endif
//#if shim_type=="CDH"
//$import parquet.hadoop.ParquetInputFormat;
//$import parquet.hadoop.ParquetRecordReader;
//#endif

import org.pentaho.di.core.RowMetaAndData;
import org.pentaho.hadoop.shim.api.format.PentahoInputFormat;
import org.pentaho.hadoop.shim.api.format.PentahoInputSplit;
import org.pentaho.hadoop.shim.api.format.PentahoRecordReader;
import org.pentaho.hadoop.shim.api.format.SchemaDescription;
import org.pentaho.hadoop.shim.common.ConfigurationProxy;

/**
 * Created by Vasilina_Terehova on 7/25/2017.
 */
public class PentahoParquetInputFormat implements PentahoInputFormat {

  private static final Logger logger = Logger.getLogger( PentahoParquetInputFormat.class );

  private final ParquetInputFormat<RowMetaAndData> nativeParquetInputFormat;
  private final Job job;
  private final TaskAttemptID taskAttemptID;

  public PentahoParquetInputFormat() {
    logger.info( "We are initializing parquet input format" );

    ConfigurationProxy conf;
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
      conf = new ConfigurationProxy();
    } finally {
      Thread.currentThread().setContextClassLoader( cl );
    }

    try {
      job = Job.getInstance( conf );
    } catch ( IOException ex ) {
      throw new RuntimeException( ex );
    }

    taskAttemptID = new TaskAttemptID();
    nativeParquetInputFormat = new ParquetInputFormat<>();

    ParquetInputFormat.setReadSupportClass( job, PentahoParquetReadSupport.class );
  }

  @Override
  public void setSchema( SchemaDescription schema ) {
    job.getConfiguration().set( ParquetConverter.PARQUET_SCHEMA_CONF_KEY, schema.marshall() );
  }

  @Override
  public void setInputFile( String file ) {
    Path filePath = new Path( file );
    try {
      ParquetInputFormat.setInputPaths( job, filePath.getParent() );
    } catch ( IOException ex ) {
      throw new RuntimeException( ex );
    }
    ParquetInputFormat.setInputDirRecursive( job, false );
    ParquetInputFormat.setInputPathFilter( job, ReadFileFilter.class );
    job.getConfiguration().set( ReadFileFilter.FILTER_DIR, filePath.getParent().toString() );
    job.getConfiguration().set( ReadFileFilter.FILTER_FILE, filePath.toString() );
  }

  @Override
  public void setSplitSize( long blockSize ) {
    ParquetInputFormat.setMaxInputSplitSize( job, blockSize );
    ParquetInputFormat.setTaskSideMetaData( job, false );
  }

  @Override
  public List<PentahoInputSplit> getSplits() throws IOException {
    List<InputSplit> splits = nativeParquetInputFormat.getSplits( job );
    return splits.stream().map( PentahoInputSplitImpl::new ).collect( Collectors.toList() );
  }

  // for parquet not actual to point split
  @Override
  public PentahoRecordReader createRecordReader( PentahoInputSplit split ) throws IOException, InterruptedException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );

      TaskAttemptContextImpl task = new TaskAttemptContextImpl( job.getConfiguration(), taskAttemptID );
      PentahoInputSplitImpl pentahoInputSplit = (PentahoInputSplitImpl) split;
      InputSplit inputSplit = pentahoInputSplit.getInputSplit();
      ParquetRecordReader<RowMetaAndData> rd =
          (ParquetRecordReader<RowMetaAndData>) nativeParquetInputFormat.createRecordReader( inputSplit, task );
      rd.initialize( inputSplit, task );
      return new PentahoParquetRecordReader( rd );

    } finally {
      Thread.currentThread().setContextClassLoader( cl );
    }
  }
}
