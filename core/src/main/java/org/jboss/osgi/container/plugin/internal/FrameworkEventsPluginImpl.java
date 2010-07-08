/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.container.plugin.internal;

//$Id: SystemPackagesPluginImpl.java 92858 2009-08-27 10:58:32Z thomas.diesler@jboss.com $

import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.BundleWrapper;
import org.jboss.osgi.container.bundle.ServiceReferenceWrapper;
import org.jboss.osgi.container.bundle.ServiceState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.util.NoFilter;
import org.jboss.osgi.container.util.RemoveOnlyCollection;
import org.jboss.osgi.spi.util.ConstantsHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.hooks.service.EventHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

/**
 * A plugin that manages {@link FrameworkListener}, {@link BundleListener}, {@link ServiceListener} and their 
 * associated {@link FrameworkEvent}, {@link BundleEvent}, {@link ServiceEvent}.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class FrameworkEventsPluginImpl extends AbstractPlugin implements FrameworkEventsPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(FrameworkEventsPluginImpl.class);

   /** The active state of this plugin */
   private boolean active;
   /** The bundle listeners */
   private final Map<Bundle, List<BundleListener>> bundleListeners = new ConcurrentHashMap<Bundle, List<BundleListener>>();
   /** The framework listeners */
   private final Map<Bundle, List<FrameworkListener>> frameworkListeners = new ConcurrentHashMap<Bundle, List<FrameworkListener>>();
   /** The service listeners */
   private final Map<Bundle, List<ServiceListenerRegistration>> serviceListeners = new ConcurrentHashMap<Bundle, List<ServiceListenerRegistration>>();

   /** The executor service */
   private ExecutorService executorService;
   /** True for synchronous event delivery */
   private boolean synchronous;
   /** The set of bundle events that are delivered to an (asynchronous) BundleListener */
   private Set<Integer> asyncBundleEvents = new HashSet<Integer>();
   /** The set of events that are logged at INFO level */
   private Set<String> infoEvents = new HashSet<String>();

   public FrameworkEventsPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      executorService = Executors.newCachedThreadPool();
      asyncBundleEvents.add(new Integer(BundleEvent.INSTALLED));
      asyncBundleEvents.add(new Integer(BundleEvent.RESOLVED));
      asyncBundleEvents.add(new Integer(BundleEvent.STARTED));
      asyncBundleEvents.add(new Integer(BundleEvent.STOPPED));
      asyncBundleEvents.add(new Integer(BundleEvent.UPDATED));
      asyncBundleEvents.add(new Integer(BundleEvent.UNRESOLVED));
      asyncBundleEvents.add(new Integer(BundleEvent.UNINSTALLED));
      infoEvents.add(ConstantsHelper.frameworkEvent(FrameworkEvent.PACKAGES_REFRESHED));
      infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.INSTALLED));
      infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.STARTED));
      infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.STOPPED));
      infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.UNINSTALLED));
   }

   public void setSynchronous(boolean synchronous)
   {
      this.synchronous = synchronous;
   }

   @Override
   public boolean isActive()
   {
      return active;
   }

   @Override
   public void setActive(boolean active)
   {
      this.active = active;
   }

   @Override
   public void addBundleListener(Bundle bundle, BundleListener listener)
   {
      if (listener == null)
         throw new IllegalArgumentException("Null listener");

      bundle = assertBundle(bundle);

      synchronized (bundleListeners)
      {
         List<BundleListener> listeners = bundleListeners.get(bundle);
         if (listeners == null)
         {
            listeners = new CopyOnWriteArrayList<BundleListener>();
            bundleListeners.put(bundle, listeners);
         }
         if (listeners.contains(listener) == false)
            listeners.add(listener);
      }
   }

   @Override
   public void removeBundleListener(Bundle bundle, BundleListener listener)
   {
      if (listener == null)
         throw new IllegalArgumentException("Null listener");

      bundle = assertBundle(bundle);

      synchronized (bundleListeners)
      {
         List<BundleListener> listeners = bundleListeners.get(bundle);
         if (listeners != null)
         {
            if (listeners.size() > 1)
               listeners.remove(listener);
            else
               removeBundleListeners(bundle);
         }
      }
   }

   @Override
   public void removeBundleListeners(Bundle bundle)
   {
      synchronized (bundleListeners)
      {
         bundle = assertBundle(bundle);
         bundleListeners.remove(bundle);
      }
   }

   @Override
   public void addFrameworkListener(Bundle bundle, FrameworkListener listener)
   {
      if (listener == null)
         throw new IllegalArgumentException("Null listener");

      bundle = assertBundle(bundle);

      synchronized (frameworkListeners)
      {
         List<FrameworkListener> listeners = frameworkListeners.get(bundle);
         if (listeners == null)
         {
            listeners = new CopyOnWriteArrayList<FrameworkListener>();
            frameworkListeners.put(bundle, listeners);
         }
         if (listeners.contains(listener) == false)
            listeners.add(listener);
      }
   }

   @Override
   public void removeFrameworkListener(Bundle bundle, FrameworkListener listener)
   {
      if (listener == null)
         throw new IllegalArgumentException("Null listener");

      bundle = assertBundle(bundle);

      synchronized (frameworkListeners)
      {
         List<FrameworkListener> listeners = frameworkListeners.get(bundle);
         if (listeners != null)
         {
            if (listeners.size() > 1)
               listeners.remove(listener);
            else
               removeFrameworkListeners(bundle);
         }
      }
   }

   @Override
   public void removeFrameworkListeners(Bundle bundle)
   {
      synchronized (frameworkListeners)
      {
         bundle = assertBundle(bundle);
         frameworkListeners.remove(bundle);
      }
   }

   @Override
   public void addServiceListener(Bundle bundle, ServiceListener listener, String filterstr) throws InvalidSyntaxException
   {
      if (listener == null)
         throw new IllegalArgumentException("Null listener");

      bundle = assertBundle(bundle);

      synchronized (serviceListeners)
      {
         List<ServiceListenerRegistration> listeners = serviceListeners.get(bundle);
         if (listeners == null)
         {
            listeners = new CopyOnWriteArrayList<ServiceListenerRegistration>();
            serviceListeners.put(bundle, listeners);
         }

         // If the context bundle's list of listeners already contains a listener l such that (l==listener), 
         // then this method replaces that listener's filter (which may be null) with the specified one (which may be null). 
         removeServiceListener(bundle, listener);

         // Create the new listener registration
         Filter filter = (filterstr != null ? FrameworkUtil.createFilter(filterstr) : NoFilter.INSTANCE);
         ServiceListenerRegistration slreg = new ServiceListenerRegistration(bundle, listener, filter);

         // The {@link ListenerHook} added method is called to provide the hook implementation with information on newly added service listeners. 
         // This method will be called as service listeners are added while this hook is registered
         for (ListenerHook hook : getServiceListenerHooks())
         {
            try
            {
               hook.added(Collections.singleton(slreg.getListenerInfo()));
            }
            catch (RuntimeException ex)
            {
               log.error("Error processing ListenerHook: " + hook, ex);
            }
         }

         // Add the listener to the list
         listeners.add(slreg);
      }
   }

   @Override
   public Collection<ListenerInfo> getServiceListenerInfos(Bundle bundle)
   {
      Collection<ListenerInfo> listeners = new ArrayList<ListenerInfo>();
      for (Entry<Bundle, List<ServiceListenerRegistration>> entry : serviceListeners.entrySet())
      {
         if (bundle == null || assertBundle(bundle).equals(entry.getKey()))
         {
            for (ServiceListenerRegistration aux : entry.getValue())
            {
               ListenerInfo info = aux.getListenerInfo();
               listeners.add(info);
            }
         }
      }
      return Collections.unmodifiableCollection(listeners);
   }

   @Override
   public void removeServiceListener(Bundle bundle, ServiceListener listener)
   {
      if (listener == null)
         throw new IllegalArgumentException("Null listener");

      bundle = assertBundle(bundle);

      synchronized (serviceListeners)
      {
         List<ServiceListenerRegistration> listeners = serviceListeners.get(bundle);
         if (listeners != null)
         {
            ServiceListenerRegistration slreg = new ServiceListenerRegistration(bundle, listener, NoFilter.INSTANCE);
            int index = listeners.indexOf(slreg);
            if (index >= 0)
            {
               slreg = listeners.remove(index);

               // The {@link ListenerHook} 'removed' method is called to provide the hook implementation with information on newly removed service listeners. 
               // This method will be called as service listeners are removed while this hook is registered. 
               for (ListenerHook hook : getServiceListenerHooks())
               {
                  try
                  {
                     ListenerInfoImpl info = (ListenerInfoImpl)slreg.getListenerInfo();
                     info.setRemoved(true);
                     hook.removed(Collections.singleton(info));
                  }
                  catch (RuntimeException ex)
                  {
                     log.error("Error processing ListenerHook: " + hook, ex);
                  }
               }
            }
         }
      }
   }

   @Override
   public void removeServiceListeners(Bundle bundle)
   {
      synchronized (serviceListeners)
      {
         Collection<ListenerInfo> listenerInfos = getServiceListenerInfos(bundle);
         serviceListeners.remove(assertBundle(bundle));

         // The {@link ListenerHook} 'removed' method is called to provide the hook implementation with information on newly removed service listeners. 
         // This method will be called as service listeners are removed while this hook is registered. 
         for (ListenerHook hook : getServiceListenerHooks())
         {
            try
            {
               hook.removed(listenerInfos);
            }
            catch (RuntimeException ex)
            {
               log.error("Error processing ListenerHook: " + hook, ex);
            }
         }
      }
   }

   private List<ListenerHook> getServiceListenerHooks()
   {
      BundleContext context = getBundleManager().getSystemContext();
      ServiceReference[] srefs = null;
      try
      {
         srefs = context.getServiceReferences(ListenerHook.class.getName(), null);
      }
      catch (InvalidSyntaxException e)
      {
         // ignore
      }
      if (srefs == null)
         return Collections.emptyList();

      List<ListenerHook> hooks = new ArrayList<ListenerHook>();
      for (ServiceReference sref : srefs)
         hooks.add((ListenerHook)context.getService(sref));

      return Collections.unmodifiableList(hooks);
   }

   @Override
   public void fireBundleEvent(final Bundle bundle, final int type)
   {
      // Get a snapshot of the current listeners
      final List<BundleListener> listeners = new ArrayList<BundleListener>();
      synchronized (bundleListeners)
      {
         for (Entry<Bundle, List<BundleListener>> entry : bundleListeners.entrySet())
         {
            for (BundleListener listener : entry.getValue())
            {
               listeners.add(listener);
            }
         }
      }

      // Expose the bundl wrapper not the state itself
      final BundleEvent event = new OSGiBundleEvent(type, assertBundle(bundle));
      final String typeName = ConstantsHelper.bundleEvent(event.getType());

      if (infoEvents.contains(ConstantsHelper.bundleEvent(event.getType())))
         log.info("Bundle " + typeName + ": " + bundle);
      else
         log.debug("Bundle " + typeName + ": " + bundle);

      // Nobody is interested
      if (listeners.isEmpty())
         return;

      // Are we active?
      if (getBundleManager().isFrameworkActive() == false)
         return;

      Runnable runnable = new Runnable()
      {
         public void run()
         {
            // Synchronous listeners first
            for (BundleListener listener : listeners)
            {
               try
               {
                  if (listener instanceof SynchronousBundleListener)
                     listener.bundleChanged(event);
               }
               catch (Throwable t)
               {
                  log.warn("Error while firing " + typeName + " for bundle " + bundle, t);
               }
            }

            // BundleListeners are called with a BundleEvent object when a bundle has been 
            // installed, resolved, started, stopped, updated, unresolved, or uninstalled
            if (asyncBundleEvents.contains(type))
            {
               for (BundleListener listener : listeners)
               {
                  try
                  {
                     if (listener instanceof SynchronousBundleListener == false)
                        listener.bundleChanged(event);
                  }
                  catch (Throwable t)
                  {
                     log.warn("Error while firing " + typeName + " for bundle " + this, t);
                  }
               }
            }
         }
      };

      // Fire the event in a runnable
      fireEvent(runnable, synchronous);
   }

   @Override
   public void fireFrameworkEvent(final Bundle bundle, final int type, final Throwable throwable)
   {
      // Get a snapshot of the current listeners
      final ArrayList<FrameworkListener> listeners = new ArrayList<FrameworkListener>();
      synchronized (frameworkListeners)
      {
         for (Entry<Bundle, List<FrameworkListener>> entry : frameworkListeners.entrySet())
         {
            for (FrameworkListener listener : entry.getValue())
            {
               listeners.add(listener);
            }
         }
      }

      // Nobody is interested
      if (listeners.isEmpty())
         return;

      // Are we active?
      if (getBundleManager().isFrameworkActive() == false)
         return;

      Runnable runnable = new Runnable()
      {
         public void run()
         {
            // Expose the wrapper not the state itself
            FrameworkEvent event = new OSGiFrameworkEvent(type, assertBundle(bundle), throwable);
            String typeName = ConstantsHelper.frameworkEvent(event.getType());

            if (infoEvents.contains(ConstantsHelper.frameworkEvent(event.getType())))
               log.info("Framwork " + typeName);
            else
               log.debug("Framwork " + typeName);

            // Nobody is interested
            if (frameworkListeners.isEmpty())
               return;

            // Are we active?
            if (getBundleManager().isFrameworkActive() == false)
               return;

            // Call the listeners
            for (FrameworkListener listener : listeners)
            {
               try
               {
                  listener.frameworkEvent(event);
               }
               catch (RuntimeException ex)
               {
                  log.warn("Error while firing " + typeName + " for framework", ex);

                  // The Framework must publish a FrameworkEvent.ERROR if a callback to an
                  // event listener generates an unchecked exception - except when the callback
                  // happens while delivering a FrameworkEvent.ERROR
                  if (type != FrameworkEvent.ERROR)
                  {
                     fireFrameworkEvent(bundle, FrameworkEvent.ERROR, ex);
                  }
               }
               catch (Throwable t)
               {
                  log.warn("Error while firing " + typeName + " for framework", t);
               }
            }
         }
      };

      // Fire the event in a runnable
      fireEvent(runnable, synchronous);
   }

   @Override
   public void fireServiceEvent(Bundle bundle, int type, final ServiceState serviceState)
   {
      // Get a snapshot of the current listeners
      List<ServiceListenerRegistration> listeners = new ArrayList<ServiceListenerRegistration>();
      synchronized (serviceListeners)
      {
         for (Entry<Bundle, List<ServiceListenerRegistration>> entry : serviceListeners.entrySet())
         {
            for (ServiceListenerRegistration listener : entry.getValue())
            {
               BundleContext context = listener.getBundleContext();
               if (context != null)
                  listeners.add(listener);
            }
         }
      }

      // Expose the wrapper not the state itself
      ServiceEvent event = new OSGiServiceEvent(type, new ServiceReferenceWrapper(serviceState));
      String typeName = ConstantsHelper.serviceEvent(event.getType());

      if (infoEvents.contains(ConstantsHelper.serviceEvent(event.getType())))
         log.info("Service " + typeName + ": " + serviceState);
      else
         log.debug("Service " + typeName + ": " + serviceState);

      // Do nothing if the Framework is not active
      if (getBundleManager().isFrameworkActive() == false)
         return;

      // Call the registered event hooks
      listeners = processEventHooks(listeners, event);

      // Nobody is interested
      if (listeners.isEmpty())
         return;

      // Call the listeners. All service events are synchronously delivered
      for (ServiceListenerRegistration listener : listeners)
      {
         try
         {
            String filterstr = listener.filter.toString();
            if (listener.filter.match(serviceState))
            {
               listener.listener.serviceChanged(event);
            }

            // The MODIFIED_ENDMATCH event is synchronously delivered after the service properties have been modified. 
            // This event is only delivered to listeners which were added with a non-null filter where 
            // the filter matched the service properties prior to the modification but the filter does 
            // not match the modified service properties. 
            else if (filterstr != null && ServiceEvent.MODIFIED == event.getType())
            {
               if (listener.filter.match(serviceState.getPreviousProperties()))
               {
                  event = new OSGiServiceEvent(ServiceEvent.MODIFIED_ENDMATCH, new ServiceReferenceWrapper(serviceState));
                  listener.listener.serviceChanged(event);
               }
            }
         }
         catch (Throwable t)
         {
            log.warn("Error while firing " + typeName + " for service " + serviceState, t);
         }
      }
   }

   private List<ServiceListenerRegistration> processEventHooks(List<ServiceListenerRegistration> listeners, final ServiceEvent event)
   {
      // Collect the BundleContexts
      Collection<BundleContext> contexts = new HashSet<BundleContext>();
      for (ServiceListenerRegistration listener : listeners)
      {
         BundleContext context = listener.getBundleContext();
         if (context != null)
            contexts.add(context);
      }
      contexts = new RemoveOnlyCollection<BundleContext>(contexts);

      // Call the registered event hooks
      List<EventHook> eventHooks = getEventHooks();
      for (EventHook hook : eventHooks)
      {
         try
         {
            hook.event(event, contexts);
         }
         catch (Exception ex)
         {
            log.warn("Error while calling EventHook: " + hook, ex);
         }
      }

      // Remove the listeners that have been filtered by the EventHooks
      if (contexts.size() != listeners.size())
      {
         Iterator<ServiceListenerRegistration> it = listeners.iterator();
         while (it.hasNext())
         {
            ServiceListenerRegistration slreg = it.next();
            if (contexts.contains(slreg.getBundleContext()) == false)
               it.remove();
         }
      }
      return listeners;
   }

   private List<EventHook> getEventHooks()
   {
      List<EventHook> hooks = new ArrayList<EventHook>();
      BundleContext context = getBundleManager().getSystemContext();
      ServiceReference[] srefs = null;
      try
      {
         srefs = context.getServiceReferences(EventHook.class.getName(), null);
      }
      catch (InvalidSyntaxException e)
      {
         // ignore
      }
      if (srefs != null)
      {
         // The calling order of the hooks is defined by the reversed compareTo ordering of their Service
         // Reference objects. That is, the service with the highest ranking number is called first. 
         List<ServiceReference> sortedRefs = new ArrayList<ServiceReference>(Arrays.asList(srefs));
         Collections.reverse(sortedRefs);

         for (ServiceReference sref : sortedRefs)
            hooks.add((EventHook)context.getService(sref));
      }
      return hooks;
   }

   private static Bundle assertBundle(Bundle bundle)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");

      // Expose the wrapper not the state itself
      if (bundle instanceof AbstractBundle)
      {
         AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
         bundle = new BundleWrapper(bundleState);
      }

      return bundle;
   }

   private void fireEvent(Runnable runnable, boolean synchronous)
   {
      if (synchronous)
      {
         runnable.run();
      }
      else
      {
         executorService.execute(runnable);
      }
   }

   /**
    * Filter and AccessControl for service events
    */
   static class ServiceListenerRegistration
   {
      private Bundle bundle;
      private ServiceListener listener;
      private Filter filter;
      private ListenerInfo info;

      // Any access control context
      AccessControlContext accessControlContext;

      ServiceListenerRegistration(Bundle bundle, ServiceListener listener, Filter filter)
      {
         if (bundle == null)
            throw new IllegalArgumentException("Null bundle");
         if (listener == null)
            throw new IllegalArgumentException("Null listener");
         if (filter == null)
            throw new IllegalArgumentException("Null filter");

         this.bundle = assertBundle(bundle);
         this.listener = listener;
         this.filter = filter;
         this.info = new ListenerInfoImpl(this);

         if (System.getSecurityManager() != null)
            accessControlContext = AccessController.getContext();
      }

      public BundleContext getBundleContext()
      {
         return bundle.getBundleContext();
      }

      public ListenerInfo getListenerInfo()
      {
         return info;
      }

      @Override
      public int hashCode()
      {
         return listener.hashCode();
      }

      @Override
      public boolean equals(Object obj)
      {
         if (obj instanceof ServiceListenerRegistration == false)
            return false;

         // Only the ServiceListener instance determins equality
         ServiceListenerRegistration other = (ServiceListenerRegistration)obj;
         return other.listener.equals(listener);
      }

      @Override
      public String toString()
      {
         String className = listener.getClass().getName();
         return "ServiceListener[" + bundle + "," + className + "," + filter + "]";
      }
   }

   static class ListenerInfoImpl implements ListenerInfo
   {
      private BundleContext context;
      private ServiceListener listener;
      private String filter;
      private boolean removed;

      ListenerInfoImpl(ServiceListenerRegistration slreg)
      {
         this.context = slreg.bundle.getBundleContext();
         this.listener = slreg.listener;
         this.filter = slreg.filter.toString();
      }

      @Override
      public BundleContext getBundleContext()
      {
         return context;
      }

      @Override
      public String getFilter()
      {
         return filter;
      }

      @Override
      public boolean isRemoved()
      {
         return removed;
      }

      public void setRemoved(boolean removed)
      {
         this.removed = removed;
      }

      @Override
      public int hashCode()
      {
         return toString().hashCode();
      }

      @Override
      public boolean equals(Object obj)
      {
         // Two ListenerInfos are equals if they refer to the same listener for a given addition and removal life cycle. 
         // If the same listener is added again, it must have a different ListenerInfo which is not equal to this ListenerInfo. 
         return super.equals(obj);
      }

      @Override
      public String toString()
      {
         String className = listener.getClass().getName();
         return "ListenerInfo[" + context + "," + className + "," + removed + "]";
      }
   }

   static class OSGiFrameworkEvent extends FrameworkEvent
   {
      private static final long serialVersionUID = 6505331543651318189L;

      public OSGiFrameworkEvent(int type, Bundle bundle, Throwable throwable)
      {
         super(type, bundle, throwable);
      }

      @Override
      public String toString()
      {
         return "FrameworkEvent[type=" + ConstantsHelper.frameworkEvent(getType()) + ",source=" + getSource() + "]";
      }
   }

   static class OSGiBundleEvent extends BundleEvent
   {
      private static final long serialVersionUID = -2705304702665185935L;

      public OSGiBundleEvent(int type, Bundle bundle)
      {
         super(type, bundle);
      }

      @Override
      public String toString()
      {
         return "BundleEvent[type=" + ConstantsHelper.bundleEvent(getType()) + ",source=" + getSource() + "]";
      }
   }

   static class OSGiServiceEvent extends ServiceEvent
   {
      private static final long serialVersionUID = 62018288275708239L;

      public OSGiServiceEvent(int type, ServiceReference reference)
      {
         super(type, reference);
      }

      @Override
      public String toString()
      {
         return "ServiceEvent[type=" + ConstantsHelper.serviceEvent(getType()) + ",source=" + getSource() + "]";
      }
   }
}