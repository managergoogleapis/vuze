/*
 * File    : ThreadPool.java
 * Created : 21-Nov-2003
 * By      : parg
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.util;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;

import com.aelitis.azureus.core.util.Java15Utils;

public class 
ThreadPool 
{
	private static final boolean	LOG_WARNINGS	= false;
	private static final int		WARN_TIME		= 10000;
	
	private static List		busy_pools			= new ArrayList();
	private static boolean	busy_pool_timer_set	= false;
	
	private static boolean	debug_thread_pool;
	private static boolean	debug_thread_pool_log_on;
	
	static{
		if ( System.getProperty("transitory.startup", "0").equals("0")){

			AEDiagnostics.addEvidenceGenerator(
				new AEDiagnosticsEvidenceGenerator()
				{
					public void
					generate(
						IndentWriter		writer )
					{
						writer.println( "Thread Pools" );
						
						try{
							writer.indent();

							List	pools;	
							
							synchronized( busy_pools ){
								
								pools	= new ArrayList( busy_pools );
							}
							
							for (int i=0;i<pools.size();i++){
								
								((ThreadPool)pools.get(i)).generateEvidence( writer );
							}
						}finally{
						
							writer.exdent();
						}
					}
				});
		}
	}
	
	private static ThreadLocal		tls	= 
		new ThreadLocal()
		{
			public Object
			initialValue()
			{
				return( null );
			}
		};
		
	protected static void
	checkAllTimeouts()
	{
		List	pools;	
	
			// copy the busy pools to avoid potential deadlock due to synchronization
			// nestings
		
		synchronized( busy_pools ){
			
			pools	= new ArrayList( busy_pools );
		}
		
		for (int i=0;i<pools.size();i++){
			
			((ThreadPool)pools.get(i)).checkTimeouts();
		}
	}
	
	
	private String	name;
	private int		max_size;
	private int		thread_name_index	= 1;
	
	private long	execution_limit;
	
	private List	busy;
	private boolean	queue_when_full;
	private List	task_queue	= new ArrayList();
	
	AESemaphore	thread_sem;
	
	private int			thread_priority	= Thread.NORM_PRIORITY;
	private boolean		warn_when_full;

	private long		task_total;
	private long		task_total_last;
	private Average		task_average	= Average.getInstance( WARN_TIME, 120 );
	
	private boolean		log_cpu;
	
	public
	ThreadPool(
		String	_name,
		int		_max_size )
	{
		this( _name, _max_size, false );
	}
	
	public
	ThreadPool(
		String	_name,
		int		_max_size,
		boolean	_queue_when_full )
	{
		name			= _name;
		max_size		= _max_size;
		queue_when_full	= _queue_when_full;
		
		thread_sem = new AESemaphore( "ThreadPool::" + name, _max_size );
		
		busy		= new ArrayList( _max_size );
	}

	private void
	generateEvidence(
		IndentWriter		writer )
	{
		writer.println( name + ": max=" + max_size +",qwf=" + queue_when_full + ",queue=" + task_queue.size() + ",busy=" + busy.size() + ",total=" + task_total + ":" + DisplayFormatters.formatDecimal(task_average.getDoubleAverage(),2) + "/sec");
	}
	
	public void
	setWarnWhenFull()
	{
		warn_when_full	= true;
	}
	
	public void
	setLogCPU()
	{
		log_cpu	= true;
	}
	
	public int
	getMaxThreads()
	{
		return( max_size );
	}
	
	public void
	setThreadPriority(
		int	_priority )
	{
		thread_priority	= _priority;
	}
	
	public void
	setExecutionLimit(
		long		millis )
	{
		execution_limit	= millis;
	}
	
	public threadPoolWorker run(AERunnable runnable) {
		return( run(runnable, false, false));
	}

	
	/**
	 * 
	 * @param runnable
	 * @param high_priority
	 *            inserts at front if tasks queueing
	 */
	public threadPoolWorker run(AERunnable runnable, boolean high_priority, boolean manualRelease) {
		
		if(manualRelease && !(runnable instanceof ThreadPoolTask))
			throw new IllegalArgumentException("manual release only allowed for ThreadPoolTasks");
		else if(manualRelease)
			((ThreadPoolTask)runnable).manualRelease = ThreadPoolTask.RELEASE_MANUAL;
		
		// System.out.println( "Thread pool:" + name + " - sem = " + thread_sem.getValue() + ", queue = " + task_queue.size());
		
			// not queueing, grab synchronous sem here
		
		if ( !queue_when_full ){
		
			if ( !thread_sem.reserveIfAvailable()){
			
					// defend against recursive entry when in queuing mode (yes, it happens)
				
				threadPoolWorker	recursive_worker = (threadPoolWorker)tls.get();
				
				if ( recursive_worker == null || recursive_worker.getOwner() != this ){
	
						// do a blocking reserve here, not recursive 
					
					checkWarning();
					
					thread_sem.reserve();
	
				}else{
						// run immediately
											
					if ( runnable instanceof ThreadPoolTask ){
						
						ThreadPoolTask task = (ThreadPoolTask)runnable;
						                        
						task.worker = recursive_worker;
						
						try{
							task.taskStarted();
							
							runIt( runnable );
							
							task.join();
							
						}finally{
							
							task.taskCompleted();  
						}
					}else{
					
						runIt( runnable );
					}

					return( recursive_worker );
				}
			}
		}
						
		threadPoolWorker allocated_worker;
						
		synchronized( this ){
		
			if ( high_priority )				
				task_queue.add( 0, runnable );
			else
				task_queue.add( runnable );
			
			// reserve if available is non-blocking
		
			if ( queue_when_full && !thread_sem.reserveIfAvailable()){
			
				allocated_worker	= null;
			
				checkWarning();
				
			}else{
				
				allocated_worker = new threadPoolWorker();	
				
			}
		}
		
		return( allocated_worker );
	}
	
	protected void
	runIt(
		AERunnable	runnable )
	{
		if ( log_cpu ){
			
			long	start_cpu = log_cpu?Java15Utils.getThreadCPUTime():0;

			runnable.run();
			
			if ( start_cpu > 0 ){
				
				long	end_cpu = log_cpu?Java15Utils.getThreadCPUTime():0;
				
				long	diff_millis = ( end_cpu - start_cpu ) / 1000000;
			
				if ( diff_millis > 10 ){
				
					System.out.println( Thread.currentThread().getName() + ": " + runnable + " -> " + diff_millis );
				}
			}	
		}else{
			
			runnable.run();
		}
	}
	
	protected void checkWarning() {
		if (warn_when_full)
		{
			String task_names = "";
			try
			{
				synchronized (ThreadPool.this)
				{
					for (int i = 0; i < busy.size(); i++)
					{
						threadPoolWorker x = (threadPoolWorker) busy.get(i);
						AERunnable r = x.runnable;
						if (x != null)
						{
							String name;
							if (r instanceof ThreadPoolTask)
								name = ((ThreadPoolTask) r).getName();
							else
								name = x.getClass().getName();
							task_names += (task_names.length() == 0 ? "" : ",") + name;
						}
					}
				}
			} catch (Throwable e)
			{}
			Debug.out("Thread pool '" + getName() + "' is full (busy=" + task_names + ")");
			warn_when_full = false;
		}
	}
	
	public AERunnable[] getQueuedTasks() {
		synchronized (this)
		{
			AERunnable[] res = new AERunnable[task_queue.size()];
			task_queue.toArray(res);
			return (res);
		}
	}
	
	public int getQueueSize() {
		synchronized (this)
		{
			return task_queue.size();
		}
	}
	
	public boolean isQueued(AERunnable task) {
		synchronized (this)
		{
			return task_queue.contains(task);
		}
	}
	
	public AERunnable[]
	getRunningTasks()
	{
		List	runnables	= new ArrayList();
		
		synchronized( this ){

			Iterator	it = busy.iterator();
			
			while( it.hasNext()){
				
				threadPoolWorker	worker = (threadPoolWorker)it.next();
				
				AERunnable	runnable = worker.getRunnable();
				
				if ( runnable != null ){
					
					runnables.add( runnable );
				}
			}
		}
		
		AERunnable[]	res = new AERunnable[runnables.size()];
			
		runnables.toArray(res);
			
		return( res );
	}
	
	public int
  	getRunningCount()
  	{
  		int	res = 0;
  		
  		synchronized( this ){

  			Iterator	it = busy.iterator();
  			
  			while( it.hasNext()){
  				
  				threadPoolWorker	worker = (threadPoolWorker)it.next();
  				
  				AERunnable	runnable = worker.getRunnable();
  				
  				if ( runnable != null ){
  					
  					res++;
  				}
  			}
  		}
  			
  		return( res );
  	}
	              	
	public boolean
	isFull()
	{
		return( thread_sem.getValue() == 0 );
	}
	
	protected void
	checkTimeouts()
	{
		synchronized( this ){
		
			long	diff = task_total - task_total_last;
			
			task_average.addValue( diff );
			
			task_total_last = task_total;
			
			if ( debug_thread_pool_log_on ){
				
				System.out.println( "ThreadPool '" + getName() + "'/" + thread_name_index + ": max=" + max_size + ",sem=[" + thread_sem.getString() + "],busy=" + busy.size() + ",queue=" + task_queue.size());
			}
			
			long	now = SystemTime.getCurrentTime();
			
			for (int i=0;i<busy.size();i++){
					
				threadPoolWorker	x = (threadPoolWorker)busy.get(i);
			
				long	elapsed = now - x.run_start_time ;
					
				if ( elapsed > ( WARN_TIME * (x.warn_count+1))){
		
					x.warn_count++;
					
					if ( LOG_WARNINGS ){
						
						DebugLight.out( x.getWorkerName() + ": running, elapsed = " + elapsed + ", state = " + x.state );
					}
					
					if ( execution_limit > 0 && elapsed > execution_limit ){
						
						if ( LOG_WARNINGS ){
							
							DebugLight.out( x.getWorkerName() + ": interrupting" );
						}
						
						AERunnable r = x.runnable;

						if ( r != null ){
							
							try{
								if ( r instanceof ThreadPoolTask ){
									
									((ThreadPoolTask)r).interruptTask();
									
								}else{
									
									x.interrupt();
								}
							}catch( Throwable e ){
								
								DebugLight.printStackTrace( e );
							}
						}
					}
				}
			}
		}
	}
	
	public String getName() {
		return (name);
	}

	void releaseManual(ThreadPoolTask toRelease) {
		if(!busy.contains(toRelease.worker) || toRelease.manualRelease != ThreadPoolTask.RELEASE_MANUAL_ALLOWED)
			throw new IllegalStateException("task already released or not manually releasable");

		synchronized (this)
		{
			long elapsed = SystemTime.getCurrentTime() - toRelease.worker.run_start_time;
			if (elapsed > WARN_TIME && LOG_WARNINGS)
				DebugLight.out(toRelease.worker.getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + toRelease.worker.state);
			
			busy.remove(toRelease.worker);
			
			// if debug is on we leave the pool registered so that we
			// can trace on the timeout events
			if (busy.size() == 0 && !debug_thread_pool)
				synchronized (busy_pools)
				{
					busy_pools.remove(this);
				}

			if(busy.size() == 0)
				thread_sem.release();
			else
				new threadPoolWorker();		
		}

	}
	
	public void registerThreadAsChild(threadPoolWorker parent)
	{
		if(tls.get() == null || tls.get() == parent)
			tls.set(parent);
		else
			throw new IllegalStateException("another parent is already set for this thread");
	}
	
	public void deregisterThreadAsChild(threadPoolWorker parent)
	{
		if(tls.get() == parent)
			tls.set(null);
		else
			throw new IllegalStateException("tls is not set to parent");
	}

	
	class threadPoolWorker extends AEThread2 {
		private final String		worker_name;
		private volatile AERunnable	runnable;
		private long				run_start_time;
		private int					warn_count;
		private String				state	= "<none>";

		protected threadPoolWorker()
		{
			super(name + "[" + (thread_name_index++) + "]",true);
			setPriority(thread_priority);
			worker_name = name + "[" + (thread_name_index++) + "]";
			start();
		}
		
		public void run() {
			tls.set(threadPoolWorker.this);
			
			boolean autoRelease = true;

			try
			{
				do
				{
					try
					{
						synchronized (ThreadPool.this)
						{
							if (task_queue.size() > 0)
								runnable = (AERunnable) task_queue.remove(0);
							else
								break;
						}
						
						synchronized (ThreadPool.this)
						{
							run_start_time = SystemTime.getCurrentTime();
							warn_count = 0;
							busy.add(threadPoolWorker.this);
							task_total++;
							if (busy.size() == 1)
							{
								synchronized (busy_pools)
								{
									if (!busy_pools.contains(ThreadPool.this))
									{
										busy_pools.add(ThreadPool.this);
										if (!busy_pool_timer_set)
										{
											// we have to defer this action rather
											// than running as a static initialiser
											// due to the dependency between
											// ThreadPool, Timer and ThreadPool again
											COConfigurationManager.addAndFireParameterListeners(new String[] { "debug.threadpool.log.enable", "debug.threadpool.debug.trace" }, new ParameterListener()
											{
												public void parameterChanged(String name) {
													debug_thread_pool = COConfigurationManager.getBooleanParameter("debug.threadpool.log.enable", false);
													debug_thread_pool_log_on = COConfigurationManager.getBooleanParameter("debug.threadpool.debug.trace", false);
												}
											});
											busy_pool_timer_set = true;
											SimpleTimer.addPeriodicEvent("ThreadPool:timeout", WARN_TIME, new TimerEventPerformer()
											{
												public void perform(TimerEvent event) {
													checkAllTimeouts();
												}
											});
										}
									}
								}
							}
						}
						
						if (runnable instanceof ThreadPoolTask)
						{
							ThreadPoolTask tpt = (ThreadPoolTask) runnable;
							tpt.worker = this;
							String task_name = tpt.getName();
							try
							{
								if (task_name != null)
									setName(worker_name + "{" + task_name + "}");
								tpt.taskStarted();
								runIt(runnable);
							} finally
							{
								if (task_name != null)
									setName(worker_name);
								
								if(tpt.isAutoReleaseAndAllowManual())
									tpt.taskCompleted();
								else
								{
									autoRelease = false;
									break;
								}
									
							}
						} else
							runIt(runnable);

					} catch (Throwable e)
					{
						DebugLight.printStackTrace(e);
					} finally
					{
						if(autoRelease)
						{
							synchronized (ThreadPool.this)
							{
								long elapsed = SystemTime.getCurrentTime() - run_start_time;
								if (elapsed > WARN_TIME && LOG_WARNINGS)
									DebugLight.out(getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + state);
								
								busy.remove(threadPoolWorker.this);
								
								// if debug is on we leave the pool registered so that we
								// can trace on the timeout events
								if (busy.size() == 0 && !debug_thread_pool)
									synchronized (busy_pools)
									{
										busy_pools.remove(ThreadPool.this);
									}
							}							
						}
					}
				} while (runnable != null);
			} catch (Throwable e)
			{
				DebugLight.printStackTrace(e);
			} finally
			{
				if(autoRelease)
					thread_sem.release();
				
				tls.set(null);
			}
		}
		
		public void setState(String _state) {
			//System.out.println( "state = " + _state );
			state = _state;
		}

		public String getState() {
			return (state);
		}

		protected String getWorkerName() {
			return (worker_name);
		}

		protected ThreadPool getOwner() {
			return (ThreadPool.this);
		}

		protected AERunnable getRunnable() {
			return (runnable);
		}
	}
}
