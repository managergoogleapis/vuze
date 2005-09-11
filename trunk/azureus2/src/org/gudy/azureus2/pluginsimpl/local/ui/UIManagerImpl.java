/*
 * Created on 19-Apr-2004
 * Created by Paul Gardner
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package org.gudy.azureus2.pluginsimpl.local.ui;

import java.util.ArrayList;
import java.util.List;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.ui.UIException;
import org.gudy.azureus2.plugins.ui.UIInstance;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;
import org.gudy.azureus2.plugins.ui.UIManagerEventListener;
import org.gudy.azureus2.plugins.ui.UIManagerListener;
import org.gudy.azureus2.plugins.ui.SWT.SWTManager;
import org.gudy.azureus2.plugins.ui.config.*;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.ui.model.PluginViewModel;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.pluginsimpl.local.ui.config.*;
import org.gudy.azureus2.pluginsimpl.local.ui.SWT.SWTManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.model.BasicPluginConfigModelImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.model.BasicPluginViewModelImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.tables.TableManagerImpl;




/**
 * @author parg
 *
 */

public class 
UIManagerImpl 
	implements UIManager
{	
	protected static AEMonitor	class_mon = new AEMonitor( "UIManager:class" );
	
	protected static boolean	initialisation_complete;
	protected static List		ui_listeners		= new ArrayList();
	protected static List		ui_event_listeners	= new ArrayList();
	protected static List		ui_instances		= new ArrayList();
	protected static List		ui_event_history	= new ArrayList();
	
	
	protected PluginInterface		pi;
	
	protected PluginConfig			plugin_config;
	protected String				key_prefix;
	
	public
	UIManagerImpl(
		PluginInterface		_pi )
	{
		pi		=_pi;
		
		plugin_config	= pi.getPluginconfig();
		
		key_prefix		= plugin_config.getPluginConfigKeyPrefix();
	}
		
	public BasicPluginViewModel
	getBasicPluginViewModel(
		String			name )
	{
		throw( new RuntimeException( "Deprecated method - use createBasicPluginViewModel"));
	}
	
	public PluginView
	createPluginView(
		PluginViewModel	model )
	{
		/*
		if ( isSWTAvailable()){
			
		  if(model instanceof BasicPluginViewModel) {
		    return new BasicPluginViewImpl((BasicPluginViewModel)model);
		  } else {
		    //throw new Exception("Unsupported Model : " + model.getClass());
		    return null;
		  }
		}else{
			return( null );
		}
		*/
		
		throw( new RuntimeException( "Deprecated method - use createBasicPluginViewModel"));
	}
	
	public BasicPluginViewModel
	createBasicPluginViewModel(
		String			name )
	{
		final BasicPluginViewModel	model = new BasicPluginViewModelImpl( name );
				
		fireEvent(
			new UIManagerEvent()
			{
				public int
				getType()
				{
					return( UIManagerEvent.ET_PLUGIN_VIEW_MODEL_CREATED );
				}
				
				public Object
				getData()
				{
					return( model );
				}
			});
		
		return( model );
	}
	
	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		section_name )
	{
		return( createBasicPluginConfigModel( null, section_name ));
	}
	
	
	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		parent_section,
		String		section_name )
	{
		final BasicPluginConfigModel	model = new BasicPluginConfigModelImpl( pi, parent_section, section_name );
		
		fireEvent(
			new UIManagerEvent()
			{
				public int
				getType()
				{
					return( UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_CREATED );
				}
				
				public Object
				getData()
				{
					return( model );
				}
			});
		
		return( model );
	}
	
	public void
	copyToClipBoard(
		final String		data )
	
		throws UIException
	{
		boolean ok = fireEvent(
				new UIManagerEvent()
				{
					public int
					getType()
					{
						return( UIManagerEvent.ET_COPY_TO_CLIPBOARD );
					}
					
					public Object
					getData()
					{
						return( data );
					}
				});
		
		if ( !ok ){
			
			throw( new UIException("Failed to deliver request to UI" ));
		}
	}

  public TableManager getTableManager() {
    return TableManagerImpl.getSingleton();
  }

  public SWTManager getSWTManager() {
    return SWTManagerImpl.getSingleton();
  }
  
  	public static void
  	initialisationComplete()
  	{
  		try{
  			class_mon.enter();
  			
  			initialisation_complete	= true;
  			
			for (int j=0;j<ui_instances.size();j++){

				UIInstance	instance = (UIInstance)ui_instances.get(j);
				
  				for (int i=0;i<ui_listeners.size();i++){
					
					try{
						((UIManagerListener)ui_listeners.get(i)).UIAttached( instance );
						
					}catch( Throwable e ){
						
						Debug.printStackTrace(e);
					}
				}  				
			}
  		}finally{
  			
  			class_mon.exit();
  		}
  	}
  
	public void
	attachUI(
		UIInstance		instance )
	{
		try{
  			class_mon.enter();
  			
  			ui_instances.add( instance );
  			
  			if ( initialisation_complete ){
  				
  				for (int i=0;i<ui_listeners.size();i++){
  					
  					try{
  						((UIManagerListener)ui_listeners.get(i)).UIAttached( instance );
  						
  					}catch( Throwable e ){
  						
  						Debug.printStackTrace(e);
  					}
  				}
  			}
  		}finally{
  			
  			class_mon.exit();
  		}		
	}
	
	public void
	detachUI(
		UIInstance		instance )
	
		throws UIException
	{
		try{
  			class_mon.enter();
  			
  			instance.detach();
  			
  			ui_instances.remove( instance );
  			
  			if ( initialisation_complete ){
  				
  				for (int i=0;i<ui_listeners.size();i++){
  					
  					try{
  						((UIManagerListener)ui_listeners.get(i)).UIDetached( instance );
  						
  					}catch( Throwable e ){
  						
  						Debug.printStackTrace(e);
  					}
  				}
  			}
  		}finally{
  			
  			class_mon.exit();
  		}		
	}
  	public void
  	addUIListener(
  		UIManagerListener listener )
  	{
		try{
  			class_mon.enter();
  			
  			ui_listeners.add( listener );
  			
 			if ( initialisation_complete ){
  				
  				for (int i=0;i<ui_instances.size();i++){
  					
  					UIInstance	instance = (UIInstance)ui_instances.get(i);

  					try{
  						listener.UIAttached( instance );
  						
  					}catch( Throwable e ){
  						
  						Debug.printStackTrace(e);
  					}
  				}
  			}
  		}finally{
  			
  			class_mon.exit();
  		} 		
  	}
  	
 	public void
  	removeUIListener(
  		UIManagerListener listener )
 	{
		try{
  			class_mon.enter();
  			
 			ui_listeners.remove( listener );
 			 
  		}finally{
  			
  			class_mon.exit();
  		}		
 	}
 	
  	public void
  	addUIEventListener(
  		UIManagerEventListener listener )
  	{
		try{
  			class_mon.enter();
  			
  			ui_event_listeners.add( listener );
  			
  		}finally{
  			
  			class_mon.exit();
  		} 
  		
  		for (int i=0;i<ui_event_history.size();i++){
  			
  			try{
  				listener.eventOccurred((UIManagerEvent)ui_event_history.get(i));
  				
  			}catch( Throwable e ){
  				
  				Debug.printStackTrace(e);
  			}
  		}
  	}
  	
 	public void
  	removeUIEventListener(
  		UIManagerEventListener listener )
 	{
		try{
  			class_mon.enter();
  			
  			ui_event_listeners.remove( listener );
 			 
  		}finally{
  			
  			class_mon.exit();
  		}		
 	}
 	
 	public static boolean
 	fireEvent(
 		UIManagerEvent	event )
 	{
 		boolean	delivered	= false;
 		
 		for (int i=0;i<ui_event_listeners.size();i++){
 			
 			try{
 				if (((UIManagerEventListener)ui_event_listeners.get(i)).eventOccurred( event )){
 					
 					delivered = true;
 					
 					break;
 				}
 				
 			}catch( Throwable e ){
 				
 				e.printStackTrace();
 			}
 		}
 		
 		int	type = event.getType();
 		
 			// some events need to be replayed when new UIs attach
 		
 		if ( 	type == UIManagerEvent.ET_PLUGIN_VIEW_MODEL_CREATED ||
 				type == UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_CREATED ){
 			
 			delivered = true;
 			
 			ui_event_history.add( event );
 		}
 		
 		return( delivered );
 	}
 	
	public void
	showTextMessage(
		final String		title_resource,
		final String		message_resource,
		final String		contents )
	{
		fireEvent(
			new UIManagerEvent()
			{
				public int
				getType()
				{
					return( UIManagerEvent.ET_SHOW_TEXT_MESSAGE );
				}
				
				public Object
				getData()
				{
					return( new String[]{ title_resource, message_resource, contents });
				}
			});
	}		
}