/*
 * Created on Feb 15, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
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


package com.aelitis.azureus.plugins.net.netstatus;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DelayedEvent;
import org.gudy.azureus2.core3.util.DirectByteBuffer;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.ConnectionEndpoint;
import com.aelitis.azureus.core.networkmanager.IncomingMessageQueue;
import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.OutgoingMessageQueue;
import com.aelitis.azureus.core.networkmanager.ProtocolEndpoint;
import com.aelitis.azureus.core.networkmanager.impl.tcp.ProtocolEndpointTCP;
import com.aelitis.azureus.core.peermanager.PeerManager;
import com.aelitis.azureus.core.peermanager.PeerManagerRegistration;
import com.aelitis.azureus.core.peermanager.PeerManagerRegistrationAdapter;
import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTBitfield;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHandshake;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHave;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessage;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageDecoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageEncoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.aelitis.azureus.core.util.CopyOnWriteList;

public class 
NetStatusProtocolTesterBT 
{
	private static Random	random = new SecureRandom();
	
	private NetStatusProtocolTester			tester;
	private CopyOnWriteList					listeners	= new CopyOnWriteList();
	
	
	private byte[]		my_hash;
	private byte[]		peer_id;
	
	private PeerManagerRegistration		pm_reg;

	private long		start_time	= SystemTime.getCurrentTime();
	
	private List		sessions	= new ArrayList();
		
	private long		outbound_attempts	= 0;
	private long		outbound_connects	= 0;
	private long		inbound_connects	= 0;

	private boolean		outbound_connections_complete;
	private AESemaphore	completion_sem = new AESemaphore( "Completion" );
	
	
	private boolean		destroyed;
	
	protected
	NetStatusProtocolTesterBT(
		NetStatusProtocolTester			_tester )
	{
		tester		= _tester;
	}
	
	protected void
	start()
	{
		my_hash = new byte[20];
		
		random.nextBytes( my_hash );
		
		peer_id = new byte[20];
		
		random.nextBytes( peer_id );
		

		pm_reg = PeerManager.getSingleton().registerLegacyManager(
			new HashWrapper( my_hash ),
			new PeerManagerRegistrationAdapter()
			{
				public byte[][]
	          	getSecrets()
				{
					return( new byte[][]{ my_hash });
				}
	          	
	          	public boolean
	          	manualRoute(
	          		NetworkConnection		connection )
	          	{
	          		log( "Got incoming connection from " + connection.getEndpoint().getNotionalAddress());
	          		
	          		new Session( connection, null );
	          			          		
	          		return( true );
	          	}
	          	
	          	public boolean
	          	isPeerSourceEnabled(
	          		String					peer_source )
	          	{
	          		return( true );
	          	}
	          	
	          	public boolean
	          	activateRequest(
	          		InetSocketAddress		remote_address )
	          	{
	          		return( true );
	          	}
	          	
	          	public void
	          	deactivateRequest(
	          		InetSocketAddress		remote_address )
	          	{
	          	}
	          	
	          	public String
	          	getDescription()
	          	{
	          		return( "NetStatusPlugin - router" );
	          	}

			});
		
		log( "Incoming routing established for " + ByteFormatter.encodeString( my_hash ));
	}
	
	protected byte[]
	getServerHash()
	{
		return( my_hash );
	}
	
	protected long
	getStartTime(
		long	now )
	{
		if ( now < start_time ){
			
			start_time = now;
		}
		
		return( start_time );
	}
	
	protected void
	testOutbound(
		InetSocketAddress		address,
		final byte[]			their_hash,
		boolean					use_crypto )
	{
			// regardless of the caller's desires, override with crypto if we're using it
		
		if ( NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE )){
			
			use_crypto = true;
		}
		
		log( "Making outbound connection to " + address );
		
		synchronized( this ){
		
			outbound_attempts++;
		}
				
		boolean	allow_fallback	= false;
		
		ProtocolEndpoint	pe = new ProtocolEndpointTCP( address );
		
		ConnectionEndpoint connection_endpoint	= new ConnectionEndpoint( address );

		connection_endpoint.addProtocol( pe );

		final NetworkConnection connection = 
			NetworkManager.getSingleton().createConnection(
					connection_endpoint, 
					new BTMessageEncoder(), 
					new BTMessageDecoder(), 
					use_crypto, 
					allow_fallback, 
					new byte[][]{ their_hash });
	
		new Session( connection, their_hash );
	}
		
	public void
	destroy()
	{
		List	to_close	= new ArrayList();
		
		synchronized( sessions ){
			
			if ( destroyed ){
				
				return;
			}
			
			destroyed = true;
			
			to_close.addAll( sessions );
			
			sessions.clear();
		}
		
		for (int i=0;i<to_close.size();i++){
			
			Session session = (Session)to_close.get(i);
			
			session.close();
		}
		
		pm_reg.unregister();
		
		checkCompletion();
		
		log( "Incoming routing destroyed for " + ByteFormatter.encodeString( my_hash ));
	}
	
	protected boolean
	isDestroyed()
	{
		return( destroyed );
	}
	
	public void
	setOutboundConnectionsComplete()
	{
		synchronized( sessions ){

			outbound_connections_complete	= true;
		}
		
		checkCompletion();
	}
	
	protected void
	checkCompletion()
	{
		boolean	inform = false;
		
		synchronized( sessions ){
	
			if ( completion_sem.isReleasedForever()){
				
				return;
			}
			
			if ( 	destroyed || 
					( outbound_connections_complete && sessions.size() == 0 )){
				
				inform = true;
				
				completion_sem.releaseForever();
			}
		}
		
		if ( inform ){
			
			Iterator it = listeners.iterator();
				
			while( it.hasNext()){
				
				try{
					((NetStatusProtocolTesterListener)it.next()).complete();
					
				}catch( Throwable e ){
					
					Debug.printStackTrace(e);
				}
			}
		}
	}
	
	public boolean
	waitForCompletion(
		long		max_millis )
	{
		if ( max_millis == 0 ){
			
			completion_sem.reserve();
			
			return( true );
			
		}else{
		
			return( completion_sem.reserve( max_millis ));
		}
	}
	
	public void
	addListener(
		NetStatusProtocolTesterListener		l )
	{
		listeners.add( l );
	}
	
	public void
	removeListener(
		NetStatusProtocolTesterListener		l )
	{
		listeners.remove( l );
	}
	
	public String
	getStatus()
	{
		return( "sessions=" + sessions.size() + 
					", out_attempts=" + outbound_attempts + 
					", out_connect=" + outbound_connects + 
					", in_connect=" + inbound_connects );
	}
	
	protected void
	log(
		String	str )
	{
		Iterator it = listeners.iterator();
		
		while( it.hasNext()){
			
			try{
				((NetStatusProtocolTesterListener)it.next()).log( str );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
		
		tester.log( str );
	}
	
	protected void
	logError(
		String	str )
	{
		Iterator it = listeners.iterator();
		
		while( it.hasNext()){
			
			try{
				((NetStatusProtocolTesterListener)it.next()).logError( str );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
		
		tester.log( str );
	}
	
	protected void
	logError(
		String		str,
		Throwable	e )
	{
		Iterator it = listeners.iterator();
		
		while( it.hasNext()){
			
			try{
				((NetStatusProtocolTesterListener)it.next()).logError( str, e );
				
			}catch( Throwable f ){
				
				Debug.printStackTrace(f);
			}
		}
		
		tester.log( str, e );
	}
	
	protected class
	Session
	{
		private NetworkConnection		connection;
		private boolean					initiator;
		private byte[]					info_hash;
		
		private boolean 	handshake_sent;
		private boolean 	bitfield_sent;

		private int			num_pieces;

		private boolean		closing;
		private boolean		closed;
		
		protected
		Session(
			NetworkConnection		_connection,
			byte[]					_info_hash )
		{
			connection	= _connection;
			info_hash	= _info_hash;

			initiator 	= info_hash != null;
			

			synchronized( sessions ){
					
				if ( destroyed ){
					
					log( "Already destroyed" );
					
					close();
					
					return;
					
				}else{
					
					sessions.add( this );
				}
			}
			
			connection.connect( 
					true,
					new NetworkConnection.ConnectionListener() 
					{
						final String	type = initiator?"Outbound":"Inbound";
						
						public final void 
						connectStarted() 
						{
							log( type + " connect start" );
						}

						public final void 
						connectSuccess( 
							ByteBuffer remaining_initial_data ) 
						{
							log( type + " connect success" );
							
							synchronized( NetStatusProtocolTesterBT.this ){
								
								if ( initiator ){
									
									outbound_connects++;
									
								}else{
									
									inbound_connects++;
								}
							}
							
							connected();
						}

						public final void 
						connectFailure( 
							Throwable e ) 
						{
							if ( !closing ){
							
								logError( type + " connection fail", e );
							}
							
							close();
						}

						public final void 
						exceptionThrown( 
							Throwable e ) 
						{
							if ( !closing ){

								logError( type + " connection fail", e );
							}
							
							close();					
						}
	    			
						public String
						getDescription()
						{
							return( "NetStatusPlugin - " + type );
						}
					});
		}
		
		protected void
		connected()
		{
			connection.getIncomingMessageQueue().registerQueueListener(
				new IncomingMessageQueue.MessageQueueListener() 
				{
					
					public boolean 
					messageReceived(
						Message message ) 
					{               
						try{
							String	message_id = message.getID();
	
							log( "Incoming message received: " + message.getID());
							
					        if ( message_id.equals( BTMessage.ID_BT_HANDSHAKE )){
						
				        		BTHandshake handshake = (BTHandshake)message;
					        		
				        		info_hash = handshake.getDataHash();
				        		
				        		num_pieces = 500 + ((int)(info_hash[0]))&0xff;
				        		
				        		if ( num_pieces%8 == 0 ){
				        			
				        			num_pieces--;
				        		}
				        		
				        		sendHandshake();
					        	
					        	sendBitfield();
					        	
					        	connection.getIncomingMessageQueue().getDecoder().resumeDecoding();
					        	
					        }else if ( message_id.equals( BTMessage.ID_BT_BITFIELD )){
									
					        	BTBitfield bitfield = (BTBitfield)message;
					  
					        	ByteBuffer bb = bitfield.getBitfield().getBuffer((byte)0);
					        	
					        	byte[]	contents = new byte[bb.remaining()];
					        	
					        	bb.get( contents );
					        						        	
					        }else if ( message_id.equals( BTMessage.ID_BT_HAVE  )){
					        	
					        	BTHave have = (BTHave)message;
					        	
					        	if ( have.getPieceNumber() == num_pieces ){
					        		
					    			synchronized( sessions ){

					    				closing = true;
					    			}
					    			
					    			close();
					        	}
					        }
					        
					        return( true );
					        
						}finally{
							
							message.destroy();
						}
					}
	  

					public final void 
					protocolBytesReceived(
						int byte_count ) 
					
					{
					}

					public final void 
					dataBytesReceived( 
						int byte_count ) 
					{
					}
				});

			connection.getOutgoingMessageQueue().registerQueueListener( 
				new OutgoingMessageQueue.MessageQueueListener() 
				{
					public final boolean 
					messageAdded( 
						Message message )
					{
						return( true );
					}
		
					public final void 
					messageQueued( 
						Message message )
					{
					}
		
					public final void 
					messageRemoved( 
						Message message )
					{
						
					}
		
					public final void 
					messageSent( 
						Message message ) 
					{
						log( "Outgoing message sent: " + message.getID());
					}
		
					public final void 
					protocolBytesSent( 
						int byte_count ) 
					{
					}
		
					public final void 
					dataBytesSent( 
						int byte_count ) 
					{
					}
					
					public void 
					flush()
					{
					}
			});

			connection.startMessageProcessing();
			
			if ( initiator ){
				
				sendHandshake();
			}
		}
		
		protected void
		sendHandshake()
		{
			if ( !handshake_sent ){
				
				handshake_sent = true;
				
				connection.getOutgoingMessageQueue().addMessage(
					new BTHandshake( info_hash, peer_id, false, BTMessageFactory.MESSAGE_VERSION_INITIAL ),
					false );
			}
		}
		
		protected void
		sendHave(
			int	piece_number )
		{
			BTHave message = new BTHave( piece_number, BTMessageFactory.MESSAGE_VERSION_INITIAL );
			
			OutgoingMessageQueue out_q = connection.getOutgoingMessageQueue();
			
			out_q.addMessage( message, false );
			
			out_q.flush();
		}
		
		protected void
		sendBitfield()
		{
			if ( !bitfield_sent ){
			
				bitfield_sent = true;
				
				byte[]	bits = new byte[( num_pieces + 7 ) /8];
				
				int	pos = 0;
				
				int i		= 0;
				int bToSend	= 0;
				
				for (; i <num_pieces; i++ ){
					
					if ((i %8) ==0){
						
						bToSend =0;
					}
					
					bToSend = bToSend << 1;
					
					if ( true ){
						
						bToSend += 1;
					}
					
					if ((i %8) ==7){
					
						bits[pos++] = (byte)bToSend;
					}
				}
				
				if ((i %8) !=0){
				
					bToSend = bToSend << (8 - (i % 8));
					
					bits[pos++] = (byte)bToSend;
				}
				
				DirectByteBuffer buffer = new DirectByteBuffer( ByteBuffer.wrap( bits ));

				connection.getOutgoingMessageQueue().addMessage(
					new BTBitfield( buffer, BTMessageFactory.MESSAGE_VERSION_INITIAL ),
					false );
			}
		}
		
		protected void
		close()
		{
			synchronized( sessions ){

				sessions.remove( this );
				
				if ( !closing ){
					
					closing	= true;

				}else{
					
					closed = true;
				}
			}

			if ( closed ){
				
				log( "Closing connection" );
				
				connection.close();
				
			}else{
				
				sendHave( num_pieces );
				
				new DelayedEvent( 
					"NetStatus:delayClose",
					5000,
					new AERunnable()
					{
						public void
						runSupport()
						{
							if ( !closed ){
								
								close();
							}
						}
					});
			}
			
			checkCompletion();
		}
	}
}
