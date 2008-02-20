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
import java.util.*;


import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ddb.DistributedDatabase;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseContact;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseException;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseKey;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseProgressListener;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseTransferHandler;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseTransferType;
import org.gudy.azureus2.plugins.ddb.DistributedDatabaseValue;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.aelitis.azureus.plugins.dht.DHTPlugin;


public class 
NetStatusProtocolTester
	implements DistributedDatabaseTransferHandler
{
	private static final int REQUEST_HISTORY_MAX	= 64;
	
	private static final int MAX_ACTIVE_TESTS	= 3;
	private static final int MAX_TEST_TIME		= 120*1000;
	
	private static final int TEST_TYPE_BT		= 1;
	
	private static final int	VERSION_INITIAL	= 1;
	
	private static final int	CURRENT_VERSION	= VERSION_INITIAL;
		
	private NetStatusPlugin		plugin;
	private PluginInterface		plugin_interface;

	private DistributedDatabase	ddb;
		
	private DHTPlugin			dht_plugin;
	
	

	private testXferType transfer_type = new testXferType();

	private Map	request_history =  
		new LinkedHashMap(REQUEST_HISTORY_MAX,0.75f,true)
		{
			protected boolean 
			removeEldestEntry(
		   		Map.Entry eldest) 
			{
				return size() > REQUEST_HISTORY_MAX;
			}
		};
		
	private List active_tests 		= new ArrayList();
	
	private TimerEventPeriodic	timer_event	= null;
		
	protected
	NetStatusProtocolTester(
		NetStatusPlugin		_plugin,
		PluginInterface		_plugin_interface )
	{
		plugin				= _plugin;
		plugin_interface	= _plugin_interface;
		
		try{
			PluginInterface dht_pi = plugin_interface.getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );
			
			if ( dht_pi != null ){
				
				dht_plugin = (DHTPlugin)dht_pi.getPlugin();
			}

			ddb = plugin_interface.getDistributedDatabase();
			
			ddb.addTransferHandler( transfer_type, this );
			
			log( "DDB transfer type registered" );
			
		}catch( Throwable e ){
			
			log( "DDB transfer type registration failed", e );
		}
	}
	
	protected void
	runTest(
		String	test_address )
	{
		NetStatusProtocolTesterBT bt_tester = new NetStatusProtocolTesterBT( this );

		addToActive( bt_tester );
		
		try{
			if ( test_address.length() == 0 ){
				
				DHT[]	dhts = dht_plugin.getDHTs();
				
				DHT	target_dht = null;
				
				int	target_network	= Constants.isCVSVersion()?DHT.NW_CVS:DHT.NW_MAIN;
				
				for (int i=0;i<dhts.length;i++){
					
					if ( dhts[i].getTransport().getNetwork() == target_network ){
						
						target_dht = dhts[i];
						
						break;
					}
				}
				
				if ( target_dht == null ){
					
					log( "DHT not found" );
					
				}else{
					
					DHTTransportContact[] contacts = target_dht.getTransport().getReachableContacts();
					
					for (int i=0;i<contacts.length;i++){
						
						DHTTransportContact dht_contact = contacts[i];
						
						DistributedDatabaseContact contact = ddb.importContact( dht_contact.getAddress());
						
						tryTest( bt_tester, contact );
					}
				}
			}else{
				
				String[]	bits = test_address.split( ":" );
				
				if ( bits.length != 2 ){
					
					log( "Invalid address - use <host>:<port> " );
					
					return;
				}
				
				InetSocketAddress address = new InetSocketAddress( bits[0].trim(), Integer.parseInt( bits[1].trim()));
				 
				DistributedDatabaseContact contact = ddb.importContact( address );

				tryTest( bt_tester, contact );
			}
		}catch( Throwable e ){
			
			log( "Test failed", e );
			
		}finally{
			
			if ( !bt_tester.isActive()){
				
				removeFromActive( bt_tester );
			}
		}
	}
	
	protected boolean
	tryTest(
		NetStatusProtocolTesterBT		bt_tester,
		DistributedDatabaseContact		contact )
	{
		log( "Trying test to " + contact.getName());
		
		Map	request = new HashMap();
		
		request.put( "v", new Long( CURRENT_VERSION ));
				
		request.put( "t", new Long( TEST_TYPE_BT ));
			
		request.put( "h", bt_tester.getServerHash());
			
		Map	reply = sendRequest( contact, request );
				
		byte[]	server_hash = reply==null?null:(byte[])reply.get( "h" );
			
		if ( server_hash != null ){
				
			log( "    " + contact.getName() + " accepted test" );
			
			bt_tester.testOutbound( adjustLoopback( contact.getAddress()), server_hash, false );
			
			return( true );
			
		}else{
			
			log( "    " + contact.getName() + " declined test" );

			return( false );
		}
	}
	
	protected InetSocketAddress
	adjustLoopback(
		InetSocketAddress	address )
	{
		InetSocketAddress local = dht_plugin.getLocalAddress().getAddress();
		
		if ( local.getAddress().getHostAddress().equals( address.getAddress().getHostAddress())){
			
			return( new InetSocketAddress( "127.0.0.1", address.getPort()));
			
		}else{
			
			return( address );
		}
	}
	
	protected Map
	sendRequest(
		DistributedDatabaseContact	contact,
		Map							request )
	{
		try{
			log( "Sending DDB request to " + contact.getName() + " - " + request );
			
			DistributedDatabaseKey key = ddb.createKey( BEncoder.encode( request ));
			
			DistributedDatabaseValue value = 
				contact.read( 
					new DistributedDatabaseProgressListener()
					{
						public void
						reportSize(
							long	size )
						{	
						}
						
						public void
						reportActivity(
							String	str )
						{	
						}
						
						public void
						reportCompleteness(
							int		percent )
						{
						}
					},
					transfer_type,
					key,
					10000 );
			
			if ( value == null ){
				
				return( null );
			}
			
			Map reply = BDecoder.decode((byte[])value.getValue( byte[].class ));
			
			log( "    received reply - " + reply );
			
			return( reply );
			
		}catch( Throwable e ){
			
			log( "sendRequest failed", e );
			
			return( null );
		}
	}
	
	protected Map
	receiveRequest(
		InetSocketAddress	originator,
		Map					request )
	{
		Map	reply = new HashMap();
		
		Long	test_type	= (Long)request.get( "t" );
		
		reply.put( "v", new Long( CURRENT_VERSION ));
		
		if ( test_type != null ){
			
			if ( test_type.intValue() == TEST_TYPE_BT ){
				
				TCPNetworkManager tcp_man = TCPNetworkManager.getSingleton();
				
				if ( 	tcp_man.isTCPListenerEnabled() &&
						tcp_man.getTCPListeningPortNumber() == ddb.getLocalContact().getAddress().getPort()){
									
						// TODO; see if we support it
					
					byte[]	their_hash	= (byte[])request.get( "h" );
					
					if ( their_hash != null ){
						
						NetStatusProtocolTesterBT bt_tester;
						
						synchronized( active_tests ){
							
							if ( active_tests.size() > MAX_ACTIVE_TESTS ){
								
								log( "Too many active tests" );
								
								return( reply );
								
							}else{
								
								bt_tester = new NetStatusProtocolTesterBT( this );
								
								addToActive( bt_tester );
							}
						}
						
						bt_tester.testOutbound( adjustLoopback( originator ), their_hash, true );
						
						reply.put( "h", bt_tester.getServerHash());
					}
				}
			}
		}		
		
		return( reply );
	}
	
	protected void
	addToActive(
		NetStatusProtocolTesterBT		tester )
	{
		synchronized( active_tests ){

			active_tests.add( tester );
			
			if ( timer_event == null ){
				
				timer_event = 
					SimpleTimer.addPeriodicEvent(
						"NetStatusProtocolTester:timer",
						30*1000,
						new TimerEventPerformer()
						{
							public void 
							perform(
								TimerEvent event )
							{
								long	now = SystemTime.getCurrentTime();
								
								List	to_remove = new ArrayList();
								
								synchronized( active_tests ){

									for (int i=0;i<active_tests.size();i++){
										
										NetStatusProtocolTesterBT tester = (NetStatusProtocolTesterBT)active_tests.get(i);
										
										long start = tester.getStartTime( now );
										
										if ( now - start > MAX_TEST_TIME ){
											
											to_remove.add( tester );
										}
									}
								}
								
								for ( int i=0;i<to_remove.size();i++ ){
									
									removeFromActive( (NetStatusProtocolTesterBT)to_remove.get(i));
								}
							}
						});
			}
		}
	}
	
	protected void
	removeFromActive(
		NetStatusProtocolTesterBT		tester )
	{
		tester.destroy();
		
		synchronized( active_tests ){
			
			active_tests.remove( tester );
			
			if ( active_tests.size() == 0 ){
				
				if ( timer_event != null ){
					
					timer_event.cancel();
					
					timer_event = null;
				}
			}
		}
	}
	
	public DistributedDatabaseValue
	read(
		DistributedDatabaseContact			contact,
		DistributedDatabaseTransferType		type,
		DistributedDatabaseKey				ddb_key )
	
		throws DistributedDatabaseException
	{
		Object	o_key = ddb_key.getKey();
		
		try{
			byte[]	key = (byte[])o_key;
			
			HashWrapper	hw = new HashWrapper( key );
			
			synchronized( request_history ){
				
				if ( request_history.containsKey( hw )){
										
					return( null );
				}
				
				request_history.put( hw, "" );
			}
			
			Map	request = BDecoder.decode( (byte[])o_key);
			
			log( "Received DDB request from " + contact.getName() + " - " + request );

			Map	result = receiveRequest( contact.getAddress(), request );
			
			return( ddb.createValue( BEncoder.encode( result )));
			
		}catch( Throwable e ){
			
			log( "DDB read failed", e );
			
			return( null );
		}
	}
	
	public void
	write(
		DistributedDatabaseContact			contact,
		DistributedDatabaseTransferType		type,
		DistributedDatabaseKey				key,
		DistributedDatabaseValue			value )
	
		throws DistributedDatabaseException
	{
		throw( new DistributedDatabaseException( "not supported" ));
	}
	
	
	public void
	log(
		String		str )
	{
		System.out.println( str );
		
		plugin.log( str );
	}
	
	public void
	log(
		String		str,
		Throwable	e )
	{
		System.out.println( str );
		e.printStackTrace();
		
		plugin.log( str, e );
	}
	
	
	protected class
	testXferType
		implements DistributedDatabaseTransferType
	{	
	}
}
