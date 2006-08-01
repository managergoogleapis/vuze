/*
 * Created on 27 Jul 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package org.gudy.azureus2.ui.swt.progress;

import org.eclipse.swt.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DelayedEvent;
import org.gudy.azureus2.ui.swt.ImageRepository;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.SWTThread;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreOperation;
import com.aelitis.azureus.core.AzureusCoreOperationListener;


public class 
ProgressWindow 
{
	public static void
	register(
		AzureusCore		core )
	{
		core.addOperationListener(
			new AzureusCoreOperationListener()
			{
				public boolean
				operationCreated(
					AzureusCoreOperation	operation )
				{
					if ( 	operation.getOperationType() == AzureusCoreOperation.OP_FILE_MOVE &&
							Utils.isThisThreadSWT()){
												
						if ( operation.getTask() != null ){
							
							new ProgressWindow( operation );
														
							return( true );
						}
					}
					
					return( false );
				}
			});
	}
	
	private volatile Shell 		shell;
	private volatile boolean 	task_complete;
	
	public 
	ProgressWindow(
		final AzureusCoreOperation	operation )
	{
		final RuntimeException[] error = {null};
		
		new DelayedEvent( 
				1000,
				new AERunnable()
				{
					public void
					runSupport()
					{
						synchronized( this ){
							
							if ( !task_complete ){
								
								Utils.execSWTThread(
									new Runnable()
									{
										public void
										run()
										{
											synchronized( this ){
												
												if ( !task_complete ){
											
													showDialog();
												}
											}
										}
									},
									false );
							}
						}
					}
				});
		
		new AEThread( "ProgressWindow", true )
		{
			public void 
			runSupport()
			{
				try{	
					// Thread.sleep(10000);
					
					operation.getTask().run( operation );
					
				}catch( RuntimeException e ){
					
					error[0] = e;
					
				}catch( Throwable e ){
		
					error[0] = new RuntimeException( e );
					
				}finally{
					
					synchronized( this ){
						
						task_complete = true;
						
						Utils.execSWTThread( new Runnable(){public void run(){}}, true );
					}			
				}
			}
		}.start();
			
		try{
			final Display display = SWTThread.getInstance().getDisplay();
	
			while( !( task_complete || display.isDisposed())){
				
				if (!display.readAndDispatch()) display.sleep();
			}
		}finally{
			
				// bit of boiler plate in case something fails in the dispatch loop
			
			synchronized( this ){
				
				task_complete = true;
			}
			
			try{
				if ( shell != null && !shell.isDisposed()){
				
					shell.dispose();
				}
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
		
		if ( error[0] != null ){
			
			throw( error[0] );
		}
	}
	
	protected void
	showDialog()
	{	
		shell = org.gudy.azureus2.ui.swt.components.shell.ShellFactory.createMainShell(
				( SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL ));

		shell.setText( MessageText.getString( "progress.window.title" ));

		if(! Constants.isOSX) {
			shell.setImage(ImageRepository.getImage("azureus"));
		}

		shell.addListener( 
				SWT.Close, 
				new Listener()
				{
					public void 
					handleEvent(
						org.eclipse.swt.widgets.Event event)
					{
						event.doit = false;
					}
				});
		
		GridLayout layout = new GridLayout();
		shell.setLayout(layout);
		
		final Display display = shell.getDisplay();
		
		try{
		    final ImageLoader loader = new ImageLoader();
		    
		    System.out.println("Image: " + "org/gudy/azureus2/ui/icons/working.gif");
		    
		    loader.load("org/gudy/azureus2/ui/icons/working.gif");
		    final Canvas canvas = new Canvas(shell,SWT.NONE);
		    final Image image = new Image(display,loader.data[0]);
		    final int[] imageNumber = {0};
		    //imageNumber[0];
		    final GC gc = new GC(image);
		    canvas.addPaintListener(new PaintListener(){
		      public void paintControl(PaintEvent event){
		     event.gc.drawImage(image,0,0);
		      }
		    });

		    
		    Thread thread = new Thread(){
		        public void run(){
		          long currentTime = System.currentTimeMillis();
		          int delayTime = loader.data[imageNumber[0]].delayTime;
		       while(currentTime + delayTime * 10 > System.currentTimeMillis()){
		            // Wait till the delay time has passed
		          }
		          display.asyncExec(new Runnable(){
		            public void run(){
		              // Increase the variable holding the frame number
		              imageNumber[0] = imageNumber[0] == loader.data.length-1 ? 0 : imageNumber[0]+1;
		              // Draw the new data onto the image
		           ImageData nextFrameData = loader.data[imageNumber[0]];
		              Image frameImage = new Image(display,nextFrameData);
		           gc.drawImage(frameImage,nextFrameData.x,nextFrameData.y);
		           frameImage.dispose();
		              canvas.redraw();
		            }
		          });
		        }
		      };

		      
			
	/*		Label label = new Label(shell, SWT.NONE);
			label.setBackgroundImage(image);
			GridData gridData = new GridData();
			gridData.horizontalSpan = 1;
			label.setLayoutData(gridData);

*/			Label label = new Label(shell, SWT.NONE);
			label.setText(MessageText.getString( "progress.window.msg.filemove" ));
			GridData gridData = new GridData();
			gridData.horizontalSpan = 1;
			label.setLayoutData(gridData);
			
/*			Browser b = new Browser(shell, SWT.FILL);
			b.setText("<span style=\"font-size: 8pt\"><img src=\"org/gudy/azureus2/ui/icons/working.gif\">" + 
							MessageText.getString( "progress.window.msg.filemove" ) + 
							"</span>");
			GridData g = new GridData();
			g.horizontalSpan = 2;
			b.setLayoutData(g);
*/			

			shell.pack();
			
			Utils.centreWindow( shell );

			shell.open();
			thread.start();
			}
		    catch(Exception e){
		    	e.printStackTrace();
		    }

/*		Label label = new Label(shell, SWT.NONE);
		label.setText(MessageText.getString( "progress.window.msg.filemove" ));
		GridData gridData = new GridData();
		label.setLayoutData(gridData);

		shell.pack();
		
		Utils.centreWindow( shell );

		shell.open();*/
	}
}
