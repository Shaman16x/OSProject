 package nachos.pa1;

import nachos.threads.*;
import nachos.machine.*;
import java.util.*;
import java.io.*;

/**
 * Coordinates a group of thread queues of the same kind.
 *
 * @see	nachos.threads.ThreadQueue
 */
public class MultiLevelScheduler extends Scheduler{

	protected static ArrayList<ThreadState> states = new ArrayList<ThreadState>();

	protected static int maxPriorityValue = 30;
	protected static int minPriorityValue = 0;
    protected long startTime = System.nanoTime();
    protected long prevTime = System.nanoTime();                     //stores the previous time call.
    protected static int agingTime = 10;
    File outfile = null;
    FileWriter file;
    PrintWriter writer;

    // for debugging purposes
    public static int getMaxPriorityValue(){
        return maxPriorityValue;
    }
    
    public static int getMinPriorityValue(){
        return minPriorityValue;
    }

    // gets the age of the scheduler in ms
    public long getSchedulerTime() {
        return (System.nanoTime() - startTime) / 1000;
    }

    //TODO print to file
    
    // prints stats about the scheduled thread
    public void printScheduledThread(ThreadState thread){
        if(outfile != null){
            try{
            file = new FileWriter(outfile, true);
            writer = new PrintWriter(file);
            writer.append(getSchedulerTime() + "," + thread.getThread().getName()+ "," + thread.getPriority());
            writer.close();
            }catch(IOException e){}
        }
        else
            System.out.println(getSchedulerTime() + "," + thread.getThread().getName()+ "," + thread.getPriority());
        
    }

    // prints the final stats of a thread that has executed
    public void printThreadStats(ThreadState thread){
        if(outfile != null){
            try{
            file = new FileWriter(outfile, true);
            writer = new PrintWriter(file);
            writer.append(thread.getStats());
            writer.close();
            }catch(IOException e){}
        }
        else
            System.out.println(thread.getStats());
    }

    // Prints Final statistics of the scheduler
    public void printFinalStats(){
        //TODO: write me
    }
    /**
     * Allocate a new scheduler.
     */
    public MultiLevelScheduler() {
        Integer i = Config.getInteger("scheduler.maxPriorityValue");
        if(i != null)
            maxPriorityValue = i;
            
        Integer ageTime = Config.getInteger("scheduler.agingTime");
        if(ageTime != null)
            agingTime = ageTime;
            
        String filename = Config.getString("statistics.logfile");
        if(!filename.equals(""))
            outfile = new File(filename);
    }
    
    /**
     * Allocate a new thread queue. If <i>transferPriority</i> is
     * <tt>true</tt>, then threads waiting on the new queue will transfer their
     * "priority" to the thread that has access to whatever is being guarded by
     * the queue. This is the mechanism used to partially solve priority
     * inversion.
     *
     * <p>
     * If there is no definite thread that can be said to have "access" (as in
     * the case of semaphores and condition variables), this parameter should
     * be <tt>false</tt>, indicating that no priority should be transferred.
     *
     * <p>
     * The processor is a special case. There is clearly no purpose to donating
     * priority to a thread that already has the processor. When the processor
     * wait queue is created, this parameter should be <tt>false</tt>.
     *
     * <p>
     * Otherwise, it is beneficial to donate priority. For example, a lock has
     * a definite owner (the thread that holds the lock), and a lock is always
     * released by the same thread that acquired it, so it is possible to help
     * a high priority thread waiting for a lock by donating its priority to
     * the thread holding the lock. Therefore, a queue for a lock should be
     * created with this parameter set to <tt>true</tt>.
     *
     * <p>
     * Similarly, when a thread is asleep in <tt>join()</tt> waiting for the
     * target thread to finish, the sleeping thread should donate its priority
     * to the target thread. Therefore, a join queue should be created with
     * this parameter set to <tt>true</tt>.
     *
     * @param	transferPriority	<tt>true</tt> if the thread that has
     *					access should receive priority from the
     *					threads that are waiting on this queue.
     * @return	a new thread queue.
     */
    public MultiLevelQueue newThreadQueue(boolean transferPriority) {
        MultiLevelQueue q = new MultiLevelQueue();
        q.setParentScheduler(this);
        return q;
    }

    /**
     * Get the priority of the specified thread. Must be called with
     * interrupts disabled.
     *
     * @param	thread	the thread to get the priority of.
     * @return	the thread's priority.
     */
    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        ThreadState s = getThreadState(thread);
        
        return s.getPriority();
    }

    public ThreadState getThreadState(KThread thread){
        ThreadState state;

        for(ThreadState s : states){
            if(s.getThread().compareTo(thread) == 0){
                return s;
            }
        }

        state = new ThreadState(thread,maxPriorityValue, maxPriorityValue, agingTime);
        states.add(state);

        return state;
    }

    /**
     * Get the priority of the current thread. Equivalent to
     * <tt>getPriority(KThread.currentThread())</tt>.
     *
     * @return	the current thread's priority.
     */
    public int getPriority() {
        return getPriority(KThread.currentThread());
    }

    /**
     * Get the effective priority of the specified thread. Must be called with
     * interrupts disabled.
     *
     * <p>
     * The effective priority of a thread is the priority of a thread after
     * taking into account priority donations.
     *
     * <p>
     * For a priority scheduler, this is the maximum of the thread's priority
     * and the priorities of all other threads waiting for the thread through a
     * lock or a join.
     *
     * <p>
     * For a lottery scheduler, this is the sum of the thread's tickets and the
     * tickets of all other threads waiting for the thread through a lock or a
     * join.
     *
     * @param	thread	the thread to get the effective priority of.
     * @return	the thread's effective priority.
     */
    public int getEffectivePriority(KThread thread) {
        int ep=maxPriorityValue;
        boolean found = false;
        for(ThreadState s:states)
            if(s.getThread().compareTo(thread)==0){
                ep = s.getEffectivePriority();
                found = true;
            }
        if(!found)
            states.add(new ThreadState(thread, maxPriorityValue, maxPriorityValue, agingTime));
        if(ep>maxPriorityValue)
            return maxPriorityValue;
        else if(ep<minPriorityValue)
            return minPriorityValue;
        else
            return ep;
    }
    
    /* 
     * updates priorities base on how much time has changed
     * currently running threads have a decreased priority (increase value)
     * waitting threads increase priority (decrease value)
     */
    public void updatePriorities(KThread currThread){
        long time = System.nanoTime() - prevTime;
        prevTime = System.nanoTime();
        
        for(ThreadState s:states){
            if(s.status == ThreadState.QueueStatus.INQUEUE){
                s.waitTime += time;
            }
            else if(s.status == ThreadState.QueueStatus.CURRENT){
                s.runTime += time;
                if(s.thread.compareTo(currThread) != 0){
                    s.status = ThreadState.QueueStatus.LIMBO;
                }
            }
        }
            
        ThreadState st = getThreadState(currThread);
        st.status = ThreadState.QueueStatus.CURRENT;
    }

    /**
     * Get the effective priority of the current thread. Equivalent to
     * <tt>getEffectivePriority(KThread.currentThread())</tt>.
     *
     * @return	the current thread's priority.
     */
    public int getEffectivePriority() {
	return getEffectivePriority(KThread.currentThread());
    }

    /**
     * Set the priority of the specified thread. Must be called with interrupts
     * disabled.
     *
     * @param	thread	the thread to set the priority of.
     * @param	priority	the new priority.
     */
    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());
        boolean found = false;
        ThreadState state;

        if(priority>maxPriorityValue) priority = maxPriorityValue;
        if(priority<minPriorityValue) priority = minPriorityValue;

        for(ThreadState s : states){
            if(s.getThread().compareTo(thread) == 0){
                found = true;
                s.setPriority(priority);
            }
        }

        if(!found){
            state = new ThreadState(thread, priority, maxPriorityValue, agingTime);
            states.add(state);
        }
    }

    /**
     * Set the priority of the current thread. Equivalent to
     * <tt>setPriority(KThread.currentThread(), priority)</tt>.
     *
     * @param	priority	the new priority.
     */
    public void setPriority(int priority) {
        setPriority(KThread.currentThread(), priority);
    }

    /**
     * If possible, raise the priority of the current thread in some
     * scheduler-dependent way.
     *
     * @return	<tt>true</tt> if the scheduler was able to increase the current
     *		thread's
     *		priority.
     */
    public boolean increasePriority() {
        return false;
    }

    /**
     * If possible, lower the priority of the current thread user in some
     * scheduler-dependent way, preferably by the same amount as would a call
     * to <tt>increasePriority()</tt>.
     *
     * @return	<tt>true</tt> if the scheduler was able to decrease the current
     *		thread's priority.
     */
    public boolean decreasePriority() {
	return false;
    }
    
}
