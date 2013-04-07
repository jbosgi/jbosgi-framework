/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.ServiceState;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.spi.RemoveOnlyCollection;
import org.jboss.osgi.resolver.spi.RemoveOnlyMap;
import org.jboss.osgi.spi.ConstantsHelper;
import org.osgi.framework.AllServiceListener;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.UnfilteredServiceListener;
import org.osgi.framework.hooks.bundle.EventHook;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

/**
 * A plugin that manages {@link FrameworkListener}, {@link BundleListener}, {@link ServiceListener} and their associated
 * {@link FrameworkEvent}, {@link BundleEvent}, {@link ServiceEvent}.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
@SuppressWarnings("deprecation")
final class FrameworkEventsImpl implements FrameworkEvents {

    private final BundleManagerPlugin bundleManager;
    private final ExecutorService executorService;
    private final LockManager lockManager;

    /** The bundleState listeners */
    private final Map<XBundle, List<BundleListenerRegistration>> bundleListeners = new ConcurrentHashMap<XBundle, List<BundleListenerRegistration>>();
    /** The framework listeners */
    private final Map<XBundle, List<FrameworkListener>> frameworkListeners = new ConcurrentHashMap<XBundle, List<FrameworkListener>>();
    /** The service listeners */
    private final Map<XBundle, List<ServiceListenerRegistration>> serviceListeners = new ConcurrentHashMap<XBundle, List<ServiceListenerRegistration>>();

    /** The set of bundleState events that are delivered to an (asynchronous) BundleListener */
    private Set<Integer> asyncBundleEvents = new HashSet<Integer>();
    /** The set of events that are logged at INFO level */
    private Set<String> infoEvents = new HashSet<String>();

    FrameworkEventsImpl(BundleManagerPlugin bundleManager, ExecutorService executorService, LockManager lockManager) {
        this.bundleManager = bundleManager;
        this.executorService = executorService;
        this.lockManager = lockManager;
        asyncBundleEvents.add(new Integer(BundleEvent.INSTALLED));
        asyncBundleEvents.add(new Integer(BundleEvent.RESOLVED));
        asyncBundleEvents.add(new Integer(BundleEvent.STARTED));
        asyncBundleEvents.add(new Integer(BundleEvent.STOPPED));
        asyncBundleEvents.add(new Integer(BundleEvent.UPDATED));
        asyncBundleEvents.add(new Integer(BundleEvent.UNRESOLVED));
        asyncBundleEvents.add(new Integer(BundleEvent.UNINSTALLED));
        infoEvents.add(ConstantsHelper.frameworkEvent(FrameworkEvent.PACKAGES_REFRESHED));
        infoEvents.add(ConstantsHelper.frameworkEvent(FrameworkEvent.ERROR));
        infoEvents.add(ConstantsHelper.frameworkEvent(FrameworkEvent.WARNING));
        infoEvents.add(ConstantsHelper.frameworkEvent(FrameworkEvent.INFO));
        infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.INSTALLED));
        infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.STARTED));
        infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.STOPPED));
        infoEvents.add(ConstantsHelper.bundleEvent(BundleEvent.UNINSTALLED));
    }

    @Override
    public void addBundleListener(final XBundle bundle, final BundleListener listener) {
        assert listener != null : "Null listener";
        synchronized (bundleListeners) {
            List<BundleListenerRegistration> registrations = bundleListeners.get(bundle);
            if (registrations == null) {
                registrations = new ArrayList<BundleListenerRegistration>();
                bundleListeners.put(bundle, registrations);
            }
            BundleListenerRegistration registration = new BundleListenerRegistration(bundle, listener);
            if (registrations.contains(registration) == false) {
                registrations.add(registration);
            }
        }
    }

    @Override
    public void removeBundleListener(final XBundle bundle, final BundleListener listener) {
        assert listener != null : "Null listener";
        synchronized (bundleListeners) {
            List<BundleListenerRegistration> registrations = bundleListeners.get(bundle);
            if (registrations != null) {
                if (registrations.size() > 1) {
                    Iterator<BundleListenerRegistration> iterator = registrations.iterator();
                    while(iterator.hasNext()) {
                        BundleListenerRegistration registration = iterator.next();
                        if (registration.getListener() == listener) {
                            iterator.remove();
                            break;
                        }
                    }
                } else {
                    removeBundleListeners(bundle);
                }
            }
        }
    }

    @Override
    public void removeBundleListeners(final XBundle bundleState) {
        synchronized (bundleListeners) {
            bundleListeners.remove(bundleState);
        }
    }

    @Override
    public void removeAllBundleListeners() {
        synchronized (bundleListeners) {
            bundleListeners.clear();
        }
    }

    @Override
    public void addFrameworkListener(final XBundle bundleState, final FrameworkListener listener) {
        assert listener != null : "Null listener";
        synchronized (frameworkListeners) {
            List<FrameworkListener> listeners = frameworkListeners.get(bundleState);
            if (listeners == null) {
                listeners = new ArrayList<FrameworkListener>();
                frameworkListeners.put(bundleState, listeners);
            }
            if (listeners.contains(listener) == false)
                listeners.add(listener);
        }
    }

    @Override
    public void removeFrameworkListener(final XBundle bundleState, final FrameworkListener listener) {
        assert listener != null : "Null listener";
        synchronized (frameworkListeners) {
            List<FrameworkListener> listeners = frameworkListeners.get(bundleState);
            if (listeners != null) {
                if (listeners.size() > 1)
                    listeners.remove(listener);
                else
                    removeFrameworkListeners(bundleState);
            }
        }
    }

    @Override
    public void removeFrameworkListeners(final XBundle bundleState) {
        synchronized (frameworkListeners) {
            frameworkListeners.remove(bundleState);
        }
    }

    @Override
    public void removeAllFrameworkListeners() {
        synchronized (frameworkListeners) {
            frameworkListeners.clear();
        }
    }

    @Override
    public void addServiceListener(final XBundle bundleState, final ServiceListener listener, final String filterstr) throws InvalidSyntaxException {
        assert listener != null : "Null listener";
        synchronized (serviceListeners) {
            List<ServiceListenerRegistration> listeners = serviceListeners.get(bundleState);
            if (listeners == null) {
                listeners = new ArrayList<ServiceListenerRegistration>();
                serviceListeners.put(bundleState, listeners);
            }

            // If the context bundleState's list of listeners already contains a listener l such that (l==listener),
            // then this method replaces that listener's filter (which may be null) with the specified one (which may be null).
            removeServiceListener(bundleState, listener);

            // Create the new listener registration
            Filter filter = (filterstr != null ? FrameworkUtil.createFilter(filterstr) : NoFilter.INSTANCE);
            ServiceListenerRegistration slreg = new ServiceListenerRegistration(bundleState, listener, filter);

            // The {@link ListenerHook} added method is called to provide the hook implementation with information on newly
            // added service listeners.
            // This method will be called as service listeners are added while this hook is registered
            for (ListenerHook hook : getServiceListenerHooks()) {
                try {
                    hook.added(Collections.singleton(slreg.getListenerInfo()));
                } catch (RuntimeException ex) {
                    LOGGER.errorProcessingServiceListenerHook(ex, hook);
                }
            }

            // Add the listener to the list
            listeners.add(slreg);
        }
    }

    @Override
    public Collection<ListenerInfo> getServiceListenerInfos(final XBundle bundleState) {
        Collection<ListenerInfo> listeners = new ArrayList<ListenerInfo>();
        for (Entry<XBundle, List<ServiceListenerRegistration>> entry : serviceListeners.entrySet()) {
            if (bundleState == null || bundleState.equals(entry.getKey())) {
                for (ServiceListenerRegistration aux : entry.getValue()) {
                    ListenerInfo info = aux.getListenerInfo();
                    listeners.add(info);
                }
            }
        }
        return Collections.unmodifiableCollection(listeners);
    }

    @Override
    public void removeServiceListener(final XBundle bundleState, final ServiceListener listener) {
        assert listener != null : "Null listener";
        synchronized (serviceListeners) {
            List<ServiceListenerRegistration> listeners = serviceListeners.get(bundleState);
            if (listeners != null) {
                ServiceListenerRegistration slreg = new ServiceListenerRegistration(bundleState, listener, NoFilter.INSTANCE);
                int index = listeners.indexOf(slreg);
                if (index >= 0) {
                    slreg = listeners.remove(index);

                    // The {@link ListenerHook} 'removed' method is called to provide the hook implementation with information
                    // on newly removed service listeners.
                    // This method will be called as service listeners are removed while this hook is registered.
                    for (ListenerHook hook : getServiceListenerHooks()) {
                        try {
                            ListenerInfo info = slreg.getListenerInfo();
                            ((ListenerInfoImpl) info).setRemoved(true);
                            hook.removed(Collections.singleton(info));
                        } catch (RuntimeException ex) {
                            LOGGER.errorProcessingServiceListenerHook(ex, hook);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void removeServiceListeners(final XBundle bundleState) {
        synchronized (serviceListeners) {
            Collection<ListenerInfo> listenerInfos = getServiceListenerInfos(bundleState);
            serviceListeners.remove(bundleState);

            // The {@link ListenerHook} 'removed' method is called to provide the hook implementation with information on newly
            // removed service listeners.
            // This method will be called as service listeners are removed while this hook is registered.
            for (ListenerHook hook : getServiceListenerHooks()) {
                try {
                    hook.removed(listenerInfos);
                } catch (RuntimeException ex) {
                    LOGGER.errorProcessingServiceListenerHook(ex, hook);
                }
            }
        }
    }

    @Override
    public void removeAllServiceListeners() {
        synchronized (serviceListeners) {
            serviceListeners.clear();
        }
    }

    private List<ListenerHook> getServiceListenerHooks() {

        if (bundleManager.isFrameworkCreated() == false)
            return Collections.emptyList();

        ServiceReference<?>[] srefs = null;
        SystemBundleState sysbundle = bundleManager.getSystemBundle();
        BundleContext syscontext = sysbundle.getBundleContext();
        try {
            srefs = syscontext.getServiceReferences(ListenerHook.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            // ignore
        }
        if (srefs == null)
            return Collections.emptyList();

        List<ListenerHook> hooks = new ArrayList<ListenerHook>();
        for (ServiceReference<?> sref : srefs)
            hooks.add((ListenerHook) syscontext.getService(sref));

        return Collections.unmodifiableList(hooks);
    }

    @Override
    public void fireBundleEvent(final XBundle bundle, final int type) {
        fireBundleEvent(null, bundle, type);
    }

    @Override
    public void fireBundleEvent(final BundleContext context, final XBundle bundle, final int type) {
        if (bundle == null)
            throw MESSAGES.illegalArgumentNull("bundle");

        // Do nothing it the framework is not active
        if (bundleManager.isFrameworkCreated() == false)
            return;

        // Get a snapshot of the current listeners
        final List<BundleListenerRegistration> registrations = new ArrayList<BundleListenerRegistration>();
        synchronized (bundleListeners) {
            for (Entry<XBundle, List<BundleListenerRegistration>> entry : bundleListeners.entrySet()) {
                for (BundleListenerRegistration blreg : entry.getValue()) {
                    registrations.add(blreg);
                }
            }
        }

        // Expose the bundleState wrapper not the state itself
        final BundleEvent event = new BundleEventImpl(type, bundle, context != null ? context.getBundle() : bundle);
        final String typeName = ConstantsHelper.bundleEvent(event.getType());

        // Call the bundle {@link EventHook}
        Collection<BundleContext> contexts = new ArrayList<BundleContext>();
        for (XBundle aux : bundleListeners.keySet()) {
            contexts.add(aux.getBundleContext());
        }
        SystemBundleState sysbundle = bundleManager.getSystemBundle();
        BundleContext syscontext = sysbundle.getBundleContext();
        contexts = new RemoveOnlyCollection<BundleContext>(contexts);
        callBundleEventHooks(syscontext, contexts, event);

        // Remove the filtered {@link BundleListener} registrations
        Iterator<BundleListenerRegistration> iterator = registrations.iterator();
        while(iterator.hasNext()) {
            BundleListenerRegistration blreg = iterator.next();
            if (!contexts.contains(blreg.getBundleContext())) {
                iterator.remove();
            }
        }

        // Nobody is interested
        if (registrations.isEmpty())
            return;

        // Sanity check that we are not holding a lock
        LockContext currentLock = lockManager.getCurrentContext();

        // Synchronous listeners first
        iterator = registrations.iterator();
        while (iterator.hasNext()) {
            BundleListenerRegistration blreg = iterator.next();
            BundleListener listener = blreg.listener;
            try {
                if (listener instanceof SynchronousBundleListener) {
                    if (currentLock != null) {
                        // This has the potential for deadlock!
                        LOGGER.debugf("Calling out to client code with current lock: %s", currentLock);
                    }
                    iterator.remove();
                    listener.bundleChanged(event);
                }
            } catch (Throwable th) {
                LOGGER.warnErrorWhileFiringBundleEvent(th, typeName, bundle);
            }
        }

        if (!registrations.isEmpty()) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    // BundleListeners are called with a BundleEvent object when a bundleState has been
                    // installed, resolved, started, stopped, updated, unresolved, or uninstalled
                    if (asyncBundleEvents.contains(type)) {
                        for (BundleListenerRegistration blreg : registrations) {
                            BundleListener listener = blreg.listener;
                            try {
                                if (!(listener instanceof SynchronousBundleListener)) {
                                    listener.bundleChanged(event);
                                }
                            } catch (Throwable th) {
                                LOGGER.warnErrorWhileFiringBundleEvent(th, typeName, bundle);
                            }
                        }
                    }
                }
            };
            if (!executorService.isShutdown()) {
                executorService.execute(runner);
            }
        }
    }

    private void callBundleEventHooks(BundleContext syscontext, Collection<BundleContext> contexts, BundleEvent event) {
        // Call the registered event listener hooks
        for (EventHook hook : getBundleEventHooks(syscontext)) {
            try {
                hook.event(event, contexts);
            } catch (Exception ex) {
                LOGGER.warnErrorWhileCallingBundleEventHook(ex, hook);
            }
        }
    }

    private List<EventHook> getBundleEventHooks(BundleContext syscontext) {

        List<EventHook> hooks = new ArrayList<EventHook>();
        Collection<ServiceReference<EventHook>> srefs = null;
        try {
            srefs = syscontext.getServiceReferences(EventHook.class, null);
        } catch (InvalidSyntaxException e) {
            // ignore
        }

        // The calling order of the hooks is defined by the reversed compareTo ordering of their Service
        // Reference objects. That is, the service with the highest ranking number is called first.
        List<ServiceReference<EventHook>> sorted = new ArrayList<ServiceReference<EventHook>>(srefs);
        Collections.reverse(sorted);

        for (ServiceReference<EventHook> sref : sorted)
            hooks.add(syscontext.getService(sref));

        return hooks;
    }

    @Override
    public void fireFrameworkEvent(final XBundle bundle, final int type, final Throwable th, final FrameworkListener... providedListeners) {

        // Do nothing it the framework is not active
        if (bundleManager.isFrameworkCreated() == false)
            return;

        // Get a snapshot of the current listeners
        final ArrayList<FrameworkListener> listeners = new ArrayList<FrameworkListener>();
        synchronized (frameworkListeners) {
            if (providedListeners != null) {
                for (FrameworkListener listener : providedListeners) {
                    if (listener != null) {
                        listeners.add(listener);
                    }
                }
            }
            for (Entry<XBundle, List<FrameworkListener>> entry : frameworkListeners.entrySet()) {
                for (FrameworkListener listener : entry.getValue()) {
                    listeners.add(listener);
                }
            }
        }

        final FrameworkEvent event = new FrameworkEventImpl(type, bundle, th);
        final String typeName = ConstantsHelper.frameworkEvent(event.getType());

        switch (event.getType()) {
            case FrameworkEvent.ERROR:
                LOGGER.errorFrameworkEvent(th);
                break;
            case FrameworkEvent.WARNING:
                LOGGER.warnFrameworkEvent(th);
                break;
            default:
                LOGGER.debugf(th, "Framework event: %s", typeName);
        }

        // Nobody is interested
        if (listeners.isEmpty())
            return;

        Runnable runner = new Runnable() {
            @Override
            public void run() {
                // Call the listeners
                for (FrameworkListener listener : listeners) {
                    try {
                        listener.frameworkEvent(event);
                    } catch (RuntimeException ex) {
                        LOGGER.warnErrorWhileFiringEvent(ex, typeName);

                        // The Framework must publish a FrameworkEvent.ERROR if a callback to an
                        // event listener generates an unchecked exception - except when the callback
                        // happens while delivering a FrameworkEvent.ERROR
                        if (type != FrameworkEvent.ERROR) {
                            fireFrameworkEvent(bundle, FrameworkEvent.ERROR, ex);
                        }
                    } catch (Throwable th) {
                        LOGGER.warnErrorWhileFiringEvent(th, typeName);
                    }
                }
            }
        };
        if (!executorService.isShutdown()) {
            executorService.execute(runner);
        }
    }

    @Override
    public void fireServiceEvent(final XBundle bundle, int type, final ServiceState<?> serviceState) {

        // Do nothing it the framework is not active
        if (bundleManager.isFrameworkCreated() == false)
            return;

        // Get a snapshot of the current listeners
        Map<BundleContext, Collection<ListenerInfo>> listeners;
        synchronized (serviceListeners) {
            listeners = new HashMap<BundleContext, Collection<ListenerInfo>>();
            for (Entry<XBundle, List<ServiceListenerRegistration>> entry : serviceListeners.entrySet()) {
                for (ServiceListenerRegistration listener : entry.getValue()) {
                    BundleContext context = listener.getBundleContext();
                    if (context != null) {
                        Collection<ListenerInfo> infos = listeners.get(context);
                        if (infos == null) {
                            infos = new ArrayList<ListenerInfo>();
                            listeners.put(context, infos);
                        }
                        infos.add(listener.getListenerInfo());
                    }
                }
            }
            for (Map.Entry<BundleContext, Collection<ListenerInfo>> entry : listeners.entrySet()) {
                listeners.put(entry.getKey(), new RemoveOnlyCollection<ListenerInfo>(entry.getValue()));
            }
            listeners = new RemoveOnlyMap<BundleContext, Collection<ListenerInfo>>(listeners);
        }

        // Construct the ServiceEvent
        ServiceEvent event = new ServiceEventImpl(type, serviceState);
        String typeName = ConstantsHelper.serviceEvent(event.getType());
        LOGGER.tracef("Service %s: %s", typeName, serviceState);

        // Call the registered event hooks
        SystemBundleState sysbundle = bundleManager.getSystemBundle();
        BundleContext syscontext = sysbundle.getBundleContext();
        callServiceEventHooks(syscontext, listeners, event);
        callServieEventListenerHooks(syscontext, listeners, event);

        // Nobody is interested
        if (listeners.isEmpty())
            return;

        // Call the listeners. All service events are synchronously delivered
        for (Map.Entry<BundleContext, Collection<ListenerInfo>> entry : listeners.entrySet()) {
            for (ListenerInfo info : entry.getValue()) {
                ServiceListenerRegistration listenerReg = ((ListenerInfoImpl) info).getRegistration();
                // Service events must only be delivered to event listeners which can validly cast the event
                if (!listenerReg.isAllServiceListener()) {
                    XBundle owner = (XBundle) info.getBundleContext().getBundle();
                    boolean assignableToOwner = true;
                    String[] clazzes = (String[]) serviceState.getProperty(Constants.OBJECTCLASS);
                    for (String clazz : clazzes) {
                        if (serviceState.isAssignableTo(owner, clazz) == false) {
                            assignableToOwner = false;
                            break;
                        }
                    }
                    if (assignableToOwner == false)
                        continue;
                }

                try {
                    String filterstr = info.getFilter();
                    ServiceListener listener = listenerReg.getListener();
                    if (listenerReg.isAllServiceListener() || listener instanceof UnfilteredServiceListener || listenerReg.filter.match(serviceState)) {
                        listener.serviceChanged(event);
                    }

                    // The MODIFIED_ENDMATCH event is synchronously delivered after the service properties have been modified.
                    // This event is only delivered to listeners which were added with a non-null filter where
                    // the filter matched the service properties prior to the modification but the filter does
                    // not match the modified service properties.
                    else if (filterstr != null && ServiceEvent.MODIFIED == event.getType()) {
                        if (listenerReg.filter.match(serviceState.getPreviousProperties())) {
                            event = new ServiceEventImpl(ServiceEvent.MODIFIED_ENDMATCH, serviceState);
                            listener.serviceChanged(event);
                        }
                    }
                } catch (Throwable th) {
                    LOGGER.warnErrorWhileFiringServiceEvent(th, typeName, serviceState);
                }
            }
        }
    }

    private void callServieEventListenerHooks(BundleContext syscontext, Map<BundleContext, Collection<ListenerInfo>> listeners, ServiceEvent event) {
        // Call the registered event listener hooks
        for (EventListenerHook hook : getServiceEventListenerHooks(syscontext)) {
            try {
                hook.event(event, listeners);
            } catch (Exception ex) {
                LOGGER.warnErrorWhileCallingEventListenerHook(ex, hook);
            }
        }
    }

    private List<EventListenerHook> getServiceEventListenerHooks(BundleContext syscontext) {

        List<EventListenerHook> hooks = new ArrayList<EventListenerHook>();
        Collection<ServiceReference<EventListenerHook>> srefs = null;
        try {
            srefs = syscontext.getServiceReferences(EventListenerHook.class, null);
        } catch (InvalidSyntaxException e) {
            // ignore
        }

        // The calling order of the hooks is defined by the reversed compareTo ordering of their Service
        // Reference objects. That is, the service with the highest ranking number is called first.
        List<ServiceReference<EventListenerHook>> sortedRefs = new ArrayList<ServiceReference<EventListenerHook>>(srefs);
        Collections.reverse(sortedRefs);

        for (ServiceReference<EventListenerHook> sref : sortedRefs)
            hooks.add(syscontext.getService(sref));

        return hooks;
    }

    private void callServiceEventHooks(BundleContext syscontext, Map<BundleContext, Collection<ListenerInfo>> listeners, final ServiceEvent event) {

        Collection<BundleContext> contexts = new RemoveOnlyCollection<BundleContext>(listeners.keySet());

        // Call the registered event hooks
        List<org.osgi.framework.hooks.service.EventHook> eventHooks = getServiceEventHooks(syscontext);
        for (org.osgi.framework.hooks.service.EventHook hook : eventHooks) {
            try {
                hook.event(event, contexts);
            } catch (Exception ex) {
                LOGGER.warnErrorWhileCallingEventHook(ex, hook);
            }
        }

        // Remove the listeners that have been filtered by the EventHooks
        if (contexts.size() != listeners.size()) {
            Iterator<BundleContext> it = listeners.keySet().iterator();
            while (it.hasNext()) {
                BundleContext context = it.next();
                if (!contexts.contains(context))
                    it.remove();
            }
        }
    }

    private List<org.osgi.framework.hooks.service.EventHook> getServiceEventHooks(BundleContext syscontext) {
        List<org.osgi.framework.hooks.service.EventHook> hooks = new ArrayList<org.osgi.framework.hooks.service.EventHook>();
        Collection<ServiceReference<org.osgi.framework.hooks.service.EventHook>> srefs = null;
        try {
            srefs = syscontext.getServiceReferences(org.osgi.framework.hooks.service.EventHook.class, null);
        } catch (InvalidSyntaxException e) {
            // ignore
        }
        if (srefs != null && !srefs.isEmpty()) {
            // The calling order of the hooks is defined by the reversed compareTo ordering of their Service
            // Reference objects. That is, the service with the highest ranking number is called first.
            List<ServiceReference<org.osgi.framework.hooks.service.EventHook>> sorted = new ArrayList<ServiceReference<org.osgi.framework.hooks.service.EventHook>>(srefs);
            Collections.reverse(sorted);

            for (ServiceReference<org.osgi.framework.hooks.service.EventHook> sref : sorted)
                hooks.add(syscontext.getService(sref));
        }
        return hooks;
    }

    /**
     * Filter and AccessControl for service events
     */
    static class ServiceListenerRegistration {

        private final XBundle bundle;
        private final ServiceListener listener;
        private final Filter filter;
        private final ListenerInfo info;

        // Any access control context
        AccessControlContext accessControlContext;

        ServiceListenerRegistration(final XBundle bundle, final ServiceListener listener, final Filter filter) {
            assert bundle != null : "Null bundle";
            assert listener != null : "Null listener";
            assert filter != null : "Null filter";
            this.bundle = bundle;
            this.listener = listener;
            this.filter = filter;
            this.info = new ListenerInfoImpl(this);
            if (System.getSecurityManager() != null)
                accessControlContext = AccessController.getContext();
        }

        XBundle getBundle() {
            return bundle;
        }

        BundleContext getBundleContext() {
            return bundle.getBundleContext();
        }

        ServiceListener getListener() {
            return listener;
        }

        ListenerInfo getListenerInfo() {
            return info;
        }

        boolean isAllServiceListener() {
            return (listener instanceof AllServiceListener);
        }

        @Override
        public int hashCode() {
            return listener.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ServiceListenerRegistration == false)
                return false;

            // Only the ServiceListener instance determins equality
            ServiceListenerRegistration other = (ServiceListenerRegistration) obj;
            return other.listener.equals(listener);
        }

        @Override
        public String toString() {
            String className = listener.getClass().getName();
            return "ServiceListener[" + bundle + "," + className + "," + filter + "]";
        }
    }

    static class BundleListenerRegistration {
        private final BundleListener listener;
        private final XBundle bundle;

        BundleListenerRegistration(XBundle bundle, BundleListener listener) {
            this.listener = listener;
            this.bundle = bundle;
        }

        BundleListener getListener() {
            return listener;
        }

        XBundle getBundle() {
            return bundle;
        }

        BundleContext getBundleContext() {
            return bundle.getBundleContext();
        }

        @Override
        public int hashCode() {
            return listener.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BundleListenerRegistration == false)
                return false;

            // Only the BundleListener instance determins equality
            BundleListenerRegistration other = (BundleListenerRegistration) obj;
            return other.listener.equals(listener);
        }

        @Override
        public String toString() {
            String className = listener.getClass().getName();
            return "BundleListener[" + bundle + "," + className + "]";
        }
    }

    static class ListenerInfoImpl implements ListenerInfo {

        private final ServiceListenerRegistration registration;
        private boolean removed;

        ListenerInfoImpl(final ServiceListenerRegistration registration) {
            this.registration = registration;
        }

        @Override
        public BundleContext getBundleContext() {
            return registration.getBundleContext();
        }

        @Override
        public String getFilter() {
            Filter filter = registration.filter;
            return filter != NoFilter.INSTANCE ? filter.toString() : null;
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }

        ServiceListenerRegistration getRegistration() {
            return registration;
        }

        void setRemoved(boolean removed) {
            this.removed = removed;
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            // Two ListenerInfos are equals if they refer to the same listener for a given addition and removal life cycle.
            // If the same listener is added again, it must have a different ListenerInfo which is not equal to this ListenerInfo.
            return super.equals(obj);
        }

        @Override
        public String toString() {
            String className = registration.listener.getClass().getName();
            return "ListenerInfo[" + getBundleContext() + "," + className + "," + removed + "]";
        }
    }

    static class FrameworkEventImpl extends FrameworkEvent {

        private static final long serialVersionUID = 6505331543651318189L;

        public FrameworkEventImpl(int type, Bundle bundle, Throwable throwable) {
            super(type, bundle, throwable);
        }

        @Override
        public String toString() {
            return "FrameworkEvent[type=" + ConstantsHelper.frameworkEvent(getType()) + ",source=" + getSource() + "]";
        }
    }

    static class BundleEventImpl extends BundleEvent {

        private static final long serialVersionUID = -2705304702665185935L;

        BundleEventImpl(int type, Bundle bundle, Bundle origin) {
            super(type, bundle, origin);
        }

        @Override
        public String toString() {
            return "BundleEvent[type=" + ConstantsHelper.bundleEvent(getType()) + ",source=" + getSource() + "]";
        }
    }

    static class ServiceEventImpl extends ServiceEvent {

        private static final long serialVersionUID = 62018288275708239L;

        public ServiceEventImpl(int type, ServiceState<?> serviceState) {
            super(type, serviceState.getReference());
        }

        @Override
        public String toString() {
            return "ServiceEvent[type=" + ConstantsHelper.serviceEvent(getType()) + ",source=" + getSource() + "]";
        }
    }
}