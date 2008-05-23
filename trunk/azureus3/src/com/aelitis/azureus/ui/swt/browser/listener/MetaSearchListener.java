package com.aelitis.azureus.ui.swt.browser.listener;

import java.io.File;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.aelitis.azureus.core.metasearch.*;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileComponent;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.swt.browser.msg.AbstractMessageListener;
import com.aelitis.azureus.ui.swt.browser.msg.BrowserMessage;
import com.aelitis.azureus.ui.swt.views.skin.Browse;
import com.aelitis.azureus.ui.swt.views.skin.SkinViewManager;


public class MetaSearchListener extends AbstractMessageListener {
	
	public static final String LISTENER_ID = "metasearch";

	public static final String OP_SEARCH				= "search";
		
	public static final String OP_GET_ENGINES 			= "get-engines";
	public static final String OP_GET_ALL_ENGINES 		= "get-all-engines";

	public static final String OP_SET_SELECTED_ENGINES 	= "set-selected-engines";
	public static final String OP_GET_AUTO_MODE		 	= "get-auto-mode";
	
	public static final String OP_SAVE_TEMPLATE		 	= "save-template";
	public static final String OP_LOAD_TEMPLATE		 	= "load-template";
	public static final String OP_DELETE_TEMPLATE		= "delete-template";
	public static final String OP_TEST_TEMPLATE			= "test-template";
	
	public static final String OP_EXPORT_TEMPLATE		= "export-template";
	public static final String OP_IMPORT_TEMPLATE		= "import-template";
	
	public static final String OP_OPEN_SEARCH_RESULTS	= "open-search-results";
	public static final String OP_CLOSE_SEARCH_RESULTS	= "close-search-results";
	
	
	public MetaSearchListener() {
		super(LISTENER_ID);
	}
	
	public void handleMessage(BrowserMessage message) {
		
		System.out.println("Got message : " + message);
		
		String opid = message.getOperationId();

		MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();
		
		if (OP_SEARCH.equals(opid)) {
			Map decodedMap = message.getDecodedMap();
			
			String searchText = (String) decodedMap.get("searchText");
			
			String headers = (String)decodedMap.get( "headers" );
			
			final Long	sid = (Long)decodedMap.get( "sid" );

			ResultListener listener = new ResultListener() {
				
				public void contentReceived(Engine engine, String content) {
					// TODO Auto-generated method stub

				}
				
				public void matchFound(Engine engine, String[] fields) {
					// TODO Auto-generated method stub
					
				}
				public void engineFailed(Engine engine, Throwable e) {
					
					context.sendBrowserMessage("metasearch", "engineFailed",getParams( engine ));
				}
				
				public void resultsComplete(Engine engine) {
				
					context.sendBrowserMessage("metasearch", "engineCompleted",getParams( engine ));
				}
				
				public void resultsReceived(Engine engine,Result[] results) {
					Map params = getParams(engine);
					List resultsList = new ArrayList(results.length);
					for(int i = 0 ; i < results.length ; i++) {
						Result result = results[i];
						System.out.println(result);
						resultsList.add(result.toMap());
					}
					params.put("results", resultsList);
					context.sendBrowserMessage("metasearch", "resultsReceived",params);
				}
				
				protected Map
				getParams(
					Engine	engine )
				{
					Map params = new HashMap();
					params.put("id", new Long(engine.getId()));
					params.put("name", engine.getName());
					params.put("favicon", engine.getIcon());
					
					if ( sid != null ){
						params.put( "sid", sid );
					}
					return( params );
				}
			};
			
			
			SearchParameter parameter = new SearchParameter("s",searchText);
			SearchParameter[] parameters = new SearchParameter[] {parameter};
			metaSearchManager.getMetaSearch().search(listener, parameters,headers);

		} else if(OP_GET_ENGINES.equals(opid)) {

			Engine[] engines = metaSearchManager.getMetaSearch().getEngines( true );
			List params = new ArrayList();
			for(int i = 0 ; i < engines.length ; i++) {
				Engine engine = engines[i];
				
				if ( !engine.isActive() || engine.getSource() == Engine.ENGINE_SOURCE_UNKNOWN ){
					continue;
				}
				Map engineMap = new HashMap();
				engineMap.put("id", new Long(engine.getId()));
				engineMap.put("name", engine.getName());
				engineMap.put("favicon", engine.getIcon());
				engineMap.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
				engineMap.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
				params.add(engineMap);
			}
			context.sendBrowserMessage("metasearch", "enginesUsed",params);
			
		} else if(OP_GET_ALL_ENGINES.equals(opid)) {

			Engine[] engines = metaSearchManager.getMetaSearch().getEngines( false );
			List params = new ArrayList();
			for(int i = 0 ; i < engines.length ; i++) {
				Engine engine = engines[i];
				
				if ( engine.getSource() == Engine.ENGINE_SOURCE_UNKNOWN ){
					continue;
				}
				
				Map engineMap = new HashMap();
				engineMap.put("id", new Long(engine.getId()));
				engineMap.put("name", engine.getName());
				engineMap.put("favicon", engine.getIcon());
				engineMap.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
				engineMap.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
				params.add(engineMap);
			}
			context.sendBrowserMessage("metasearch", "engineList",params);
			
		} else if( OP_SET_SELECTED_ENGINES.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			List template_ids = (List)decodedMap.get( "template_ids" );
			
			long[] ids = new long[template_ids.size()];
			
			for (int i=0;i<ids.length;i++ ){
				
				Map m = (Map)template_ids.get(i);
				
				ids[i] = ((Long)m.get("id")).longValue();
			}
			
			boolean	auto = ((Boolean)decodedMap.get( "auto" )).booleanValue();
			
			try{
				metaSearchManager.setSelectedEngines( ids, auto );
				
				Map params = new HashMap();
				context.sendBrowserMessage("metasearch", "setSelectedCompleted",params);

			}catch( Throwable e ){
				
				Map params = new HashMap();
				params.put("error",Debug.getNestedExceptionMessage(e));

				context.sendBrowserMessage("metasearch", "setSelectedFailed",params);
			}	
		} else if(OP_GET_AUTO_MODE.equals(opid)) {
						
			boolean mode = metaSearchManager.isAutoMode();
			
			Map params = new HashMap();
			params.put( "auto", new Boolean( mode ));

			context.sendBrowserMessage("metasearch", "getAutoModeResult",params);
			
		} else if( OP_SAVE_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			String	type_str = (String)decodedMap.get( "type" );
			
			String	name = (String)decodedMap.get( "name" );
			
			Long	l_id	= (Long)decodedMap.get( "id" );
			
			long	id = l_id == null?-1:l_id.longValue();
			
			String	json = (String)decodedMap.get( "value" );
			
			try{
				Engine engine = 
					metaSearchManager.addEngine( 
							id, 
							type_str.equals( "json" )?Engine.ENGINE_TYPE_JSON:Engine.ENGINE_TYPE_REGEX, 
							name, 
							json );
				
				Map params = new HashMap();
				params.put( "id", new Long( engine.getId() ));
	
				context.sendBrowserMessage( "metasearch", "saveTemplateCompleted", params );
				
			}catch( Throwable e ){
				
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put("error",Debug.getNestedExceptionMessage(e));

				context.sendBrowserMessage("metasearch", "saveTemplateFailed",params);
			}
		} else if( OP_LOAD_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			long	id	= ((Long)decodedMap.get( "id" )).longValue();
			
			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
		
			if ( engine == null ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put("error","Template not found");

				context.sendBrowserMessage("metasearch", "loadTemplateFailed",params);
				
			}else{
				
				try{
					Map params = new HashMap();
					params.put("id", new Long(engine.getId()));
					params.put("name", engine.getName());
					params.put("type", Engine.ENGINE_TYPE_STRS[ engine.getType()]);
					params.put("value", JSONObject.escape( engine.exportToJSONString()));
				
					context.sendBrowserMessage( "metasearch", "loadTemplateCompleted", params );
					
				}catch( Throwable e ){
					
					Map params = new HashMap();
					params.put( "id", new Long( id ));
					params.put("error",Debug.getNestedExceptionMessage(e));

					context.sendBrowserMessage("metasearch", "loadTemplateFailed",params);
				}
			}		
		} else if( OP_DELETE_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			long	id	= ((Long)decodedMap.get( "id" )).longValue();
			
			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
			
			if ( engine == null ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put( "error", "Template not found" );

				context.sendBrowserMessage("metasearch", "deleteTemplateFailed",params);
			
			}else if ( engine.getSource() != Engine.ENGINE_SOURCE_LOCAL ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put( "error", "Template is not local" );

				context.sendBrowserMessage("metasearch", "deleteTemplateFailed",params);
				
			}else{
				
				engine.delete();
				
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				context.sendBrowserMessage( "metasearch", "deleteTemplateCompleted", params );
			}
		} else if( OP_TEST_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			final long	id		= ((Long)decodedMap.get( "id" )).longValue();
			long	match_count	= ((Long)decodedMap.get( "max_matches" )).longValue();
			
			String searchText 	= (String) decodedMap.get("searchText");
			String headers		= (String) decodedMap.get("headers");
			
			final Long	sid = (Long)decodedMap.get( "sid" );

			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
			
			if ( engine == null ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put( "error", "Template not found" );
				if ( sid != null )params.put( "sid", sid );

				context.sendBrowserMessage("metasearch", "testTemplateFailed",params);
			
			}else{
				
				SearchParameter parameter = new SearchParameter("s",searchText);
				SearchParameter[] parameters = new SearchParameter[] {parameter};

				engine.search(
						parameters, 
						(int)match_count,
						headers,
						new ResultListener()
						{
							private String	content;
							private List	matches = new ArrayList();
							
							public void 
							contentReceived(
								Engine 		engine, 
								String 		_content )
							{
								content = _content;
							}
							
							public void 
							matchFound(
								Engine 		engine,
								String[] 	fields) 
							{
								matches.add( fields );
							}
							
							public void 
							resultsReceived(
								Engine 		engine,
								Result[] 	results )
							{								
							}
							
							public void 
							resultsComplete(
								Engine 		engine )
							{
								Map params = new HashMap();
								params.put( "id", new Long( id ));
								if ( sid != null )params.put( "sid", sid );
								params.put( "content", JSONObject.escape( content ));

								JSONArray	l_matches = new JSONArray();
								
								params.put( "matches", l_matches );
								
								for (int i=0;i<matches.size();i++){
									
									String[]	match = (String[])matches.get(i);
									
									JSONArray	l_match = new JSONArray();
									
									l_matches.add( l_match );
									
									for (int j=0;j<match.length;j++){
										
										l_match.add( match[j] );
									}
								}
															
								context.sendBrowserMessage( "metasearch", "testTemplateCompleted", params );
			
							}
							
							public void 
							engineFailed(
								Engine 		engine,
								Throwable 	e )
							{
								Map params = new HashMap();
								params.put( "id", new Long( id ));
								params.put( "error", Debug.getNestedExceptionMessage( e ));
								if ( sid != null )params.put( "sid", sid );

								context.sendBrowserMessage("metasearch", "testTemplateFailed",params);
							}
						});

			}	
		} else if ( OP_EXPORT_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			final long	id		= ((Long)decodedMap.get( "id" )).longValue();
			
			final Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
			
			if ( engine == null ){
				
				Map params = new HashMap();
				params.put( "error", "template '" + id + "' not found" );

				context.sendBrowserMessage("metasearch", "exportTemplateFailed",params);
				
			}else{
				final Shell shell = Utils.findAnyShell();
				
				shell.getDisplay().asyncExec(
					new AERunnable() 
					{
						public void 
						runSupport()
						{
							FileDialog dialog = 
								new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.SAVE );
							
							dialog.setFilterPath( TorrentOpener.getFilterPathData() );
													
							dialog.setText(MessageText.getString("metasearch.export.select.template.file"));
							
							dialog.setFilterExtensions(new String[] {
									"*.vuze",
									"*.vuz",
									Constants.FILE_WILDCARD
								});
							dialog.setFilterNames(new String[] {
									"*.vuze",
									"*.vuz",
									Constants.FILE_WILDCARD
								});
							
							String path = TorrentOpener.setFilterPathData( dialog.open());
		
							if ( path != null ){
								
								try{
									engine.exportToVuzeFile( new File( path ));
									
									Map params = new HashMap();
									params.put( "id", new Long( id ));
									context.sendBrowserMessage( "metasearch", "exportTemplateCompleted", params );

								}catch( Throwable e ){
									
									Map params = new HashMap();
									params.put( "id", new Long( id ));
									params.put( "error", "save failed: " + Debug.getNestedExceptionMessage(e));

									context.sendBrowserMessage("metasearch", "exportTemplateFailed",params);
								}
							}else{
								
								Map params = new HashMap();
								params.put( "id", new Long( id ));
								params.put( "error", "operation cancelled" );

								context.sendBrowserMessage("metasearch", "exportTemplateFailed",params);
							}
						}
					});
			}
		}else if ( OP_IMPORT_TEMPLATE.equals(opid)){
						
			final Shell shell = Utils.findAnyShell();
			
			shell.getDisplay().asyncExec(
				new AERunnable() 
				{
					public void 
					runSupport()
					{
						FileDialog dialog = 
							new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.OPEN );
						
						dialog.setFilterPath( TorrentOpener.getFilterPathData() );
												
						dialog.setText(MessageText.getString("metasearch.import.select.template.file"));
						
						dialog.setFilterExtensions(new String[] {
								"*.vuze",
								"*.vuz",
								Constants.FILE_WILDCARD
							});
						dialog.setFilterNames(new String[] {
								"*.vuze",
								"*.vuz",
								Constants.FILE_WILDCARD
							});
						
						String path = TorrentOpener.setFilterPathData( dialog.open());
	
						if ( path != null ){
							
							VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
							
							VuzeFile vf = vfh.loadAndHandleVuzeFile( path, VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE );
							
							if ( vf == null ){
								
								Map params = new HashMap();
								params.put( "error", "invalid .vuze file" );

								context.sendBrowserMessage("metasearch", "importTemplateFailed",params);
								
							}else{
								
								VuzeFileComponent[] comps = vf.getComponents();
								
								for (int i=0;i<comps.length;i++){
									
									VuzeFileComponent comp = comps[i];
									
									if ( comp.getType() == VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE ){
										
										Engine engine = (Engine)comp.getData( Engine.VUZE_FILE_COMPONENT_ENGINE_KEY );
										
										if ( engine != null ){
											
											Map params = new HashMap();
											params.put( "id", new Long( engine.getId()));
											context.sendBrowserMessage( "metasearch", "importTemplateCompleted", params );

											return;
										}
									}
								}
								
								Map params = new HashMap();
								params.put( "error", "invalid search template file" );

								context.sendBrowserMessage("metasearch", "importTemplateFailed",params);
							}
						}else{
							
							Map params = new HashMap();
							params.put( "error", "operation cancelled" );

							context.sendBrowserMessage("metasearch", "importTemplateFailed",params);
						}
					}
				});
		} else if ( OP_OPEN_SEARCH_RESULTS.equals(opid)){
			Map decodedMap = message.getDecodedMap();
			Browse view = (Browse) SkinViewManager.get(Browse.class);
			view.openSearchResults(decodedMap);
		} else if ( OP_CLOSE_SEARCH_RESULTS.equals(opid)){
			Map decodedMap = message.getDecodedMap();
			Browse view = (Browse) SkinViewManager.get(Browse.class);
			view.closeSearchResults(decodedMap);
		}
	}

}
