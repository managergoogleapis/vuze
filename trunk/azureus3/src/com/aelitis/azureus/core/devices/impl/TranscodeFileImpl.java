/*
 * Created on Feb 18, 2009
 * Created by Paul Gardner
 * 
 * Copyright 2009 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


/**
 * 
 */
package com.aelitis.azureus.core.devices.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.disk.DiskManagerFileInfo;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.utils.StaticUtilities;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.devices.TranscodeException;
import com.aelitis.azureus.core.devices.TranscodeFile;
import com.aelitis.azureus.core.devices.TranscodeProviderAnalysis;
import com.aelitis.azureus.core.devices.TranscodeTargetListener;
import com.aelitis.azureus.core.download.DiskManagerFileInfoFile;
import com.aelitis.azureus.util.ImportExportUtils;

class
TranscodeFileImpl
	implements TranscodeFile
{	
	private DeviceImpl					device;
	private String						key;
	private Map<String,Map<String,?>>	files_map;
	
		// don't store any local state here, store it in the map as this is just a wrapper
		// for the underlying map and there can be multiple such wrappers concurrent
	
	protected 
	TranscodeFileImpl(
		DeviceImpl					_device,
		String						_key,
		Map<String,Map<String,?>>	_files_map,
		File						_file )
	{
		device		= _device;
		key			= _key;
		files_map	= _files_map;

		getMap( true );
		
		setString( "file", _file.getAbsolutePath());
	}
	
	protected
	TranscodeFileImpl(
		DeviceImpl					_device,
		String						_key,
		Map<String,Map<String,?>>	_map )
	
		throws IOException
	{
		device			= _device;
		key				= _key;
		files_map		= _map;
		
		Map<String,?> map = getMap();
		
		if ( map == null || !map.containsKey( "file" )){
			
			throw( new IOException( "File has been deleted" ));
		}
	}
	
	
	protected String
	getKey()
	{
		return( key );
	}
	
	public File 
	getCacheFile() 
	{
		return(new File(getString("file")));
	}
		
	public DiskManagerFileInfo 
	getSourceFile() 
	{
		// options are 1) cached file 2) link to other file 3) download-file 
		
		String	hash = getString( "sf_hash" );
		
		if ( hash != null ){
			
			try{
				Download download = StaticUtilities.getDefaultPluginInterface().getDownloadManager().getDownload( Base32.decode(hash));
				
				if ( download != null ){
					
					int index = (int)getLong( "sf_index" );
					
					return( download.getDiskManagerFileInfo()[index] );
				}
				
			}catch( Throwable e ){
				
			}
		}
		
		File	file = getCacheFile();
		
		if ( !file.exists() || file.length() == 0 ){
			
			String	link = getString( "sf_link" );
			
			if ( link != null ){
				
				File link_file = new File( link );
				
				if ( link_file.exists()){
					
					file = link_file;
				}
			}
		}
		
		return( new DiskManagerFileInfoFile( file ));
	}
	
	protected void
	setSourceFile(
		DiskManagerFileInfo		file )
	{
		try{
			Download download = file.getDownload();
			
			if ( download != null && download.getTorrent() != null ){
				
				setString( "sf_hash", Base32.encode( download.getTorrent().getHash() ));
				setLong( "sf_index", file.getIndex());
			}
		}catch( Throwable e ){
			
		}
		
		setString( "sf_link", file.getFile().getAbsolutePath());
	}
	
	protected void
	setComplete(
		boolean b )
	{
		setLong( PT_COMPLETE, b?1:0 );
	}
	
	public boolean
	isComplete()
	{
		return( getLong( PT_COMPLETE ) == 1 );
	}
	
	protected void
	setCopiedToDevice(
		boolean b )
	{
		setLong( PT_COPIED, b?1:0 );
	}
	
	public boolean
	isCopiedToDevice()
	{
		return( getLong( PT_COPIED ) == 1 );
	}
	
	protected void
	update(
		TranscodeProviderAnalysis		analysis )
	{
		long	duration		= analysis.getLongProperty( TranscodeProviderAnalysis.PT_DURATION_MILLIS );
		long	video_width		= analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_WIDTH );
		long	video_height	= analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_HEIGHT );

	}
	
	public void
	delete(
		boolean	delete_contents )
	
		throws TranscodeException 
	{
		device.deleteFile( this, delete_contents );
	}
	
	public boolean
	isDeleted()
	{
		return( getMap() == null );
	}
	
	private Map<String,?>
	getMap()
	{
		return( getMap( false ));
	}
	
	private Map<String,?>
	getMap(
		boolean	create )
	{		
		synchronized( files_map ){
	
			Map<String,?> map = files_map.get( key );
			
			if ( map == null && create ){
				
				map = new HashMap<String, Object>();
				
				files_map.put( key, map );
			}
			
			return( map );
		}
	}
	
	protected long
	getLong(
		String		key )
	{
		try{
			Map<String,?>	map = getMap();
			
			return(ImportExportUtils.importLong( map, key, 0 ));
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( 0 );
		}
	}
	
	protected void
	setLong(
		String		key,
		long		value )
	{	
		synchronized( files_map ){

			try{
				Map<String,?>	map = getMap();

				ImportExportUtils.exportLong( map, key, value);
				
				device.fileDirty( this, TranscodeTargetListener.CT_PROPERTY, key );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	protected String
	getString(
		String		key )
	{
		try{
			Map<String,?>	map = getMap();

			return(ImportExportUtils.importString( map, key ));
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( "" );
		}
	}
	
	protected void
	setString(
		String		key,
		String		value )
	{
		synchronized( files_map ){
			
			Map<String,?>	map = getMap();
			
			try{
				ImportExportUtils.exportString( map, key, value );
				
				device.fileDirty( this, TranscodeTargetListener.CT_PROPERTY, key );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	public void
	setTransientProperty(
		Object		key2,
		Object		value )
	{
		device.setTransientProperty( key, key2, value );
	}
			
	public Object
	getTransientProperty(
		Object		key2 )
	{
		return( device.getTransientProperty( key, key2 ));
	}
	
	public boolean
	equals(
		Object	other )
	{
		if ( other instanceof TranscodeFileImpl ){
			
			return( key.equals(((TranscodeFileImpl)other).key));
		}
		
		return( false );
	}
	
	public int
	hashCode()
	{
		return( key.hashCode());
	}
}