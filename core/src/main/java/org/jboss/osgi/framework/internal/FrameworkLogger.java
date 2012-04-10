/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.osgi.framework.internal;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.io.File;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Cause;
import org.jboss.logging.LogMessage;
import org.jboss.logging.Logger;
import org.jboss.logging.Message;
import org.jboss.logging.MessageLogger;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.EventHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.framework.wiring.BundleRevision;

/**
 * Logging Id ranges: 11000-11199
 *
 * https://docs.jboss.org/author/display/JBOSGI/JBossOSGi+Logging
 *
 * @author Thomas.Diesler@jboss.com
 */
@MessageLogger(projectCode = "JBOSGI")
public interface FrameworkLogger extends BasicLogger {

    FrameworkLogger LOGGER = Logger.getMessageLogger(FrameworkLogger.class, "org.jboss.osgi.framework");

    @LogMessage(level = INFO)
    @Message(id = 11000, value = "OSGi Framework started")
    void infoFrameworkStarted();

    @LogMessage(level = INFO)
    @Message(id = 11001, value = "Bundle started: %s")
    void infoBundleStarted(Bundle bundle);
    
    @LogMessage(level = INFO)
    @Message(id = 11002, value = "Bundle stopped: %s")
    void infoBundleStopped(Bundle bundle);
    
    @LogMessage(level = INFO)
    @Message(id = 11003, value = "Bundle updated: %s")
    void infoBundleUpdated(Bundle bundle);
    @LogMessage(level = INFO)
    @Message(id = 11004, value = "%s - %s")
    void infoFrameworkImplementation(String implementationTitle, String implementationVersion);
    
    @LogMessage(level = INFO)
    @Message(id = 11005, value = "Increasing start level from %d to %d")
    void infoIncreasingStartLevel(int fromLevel, int toLevel);
    
    @LogMessage(level = INFO)
    @Message(id = 11006, value = "Decreasing start level from %d to %d")
    void infoDecreasingStartLevel(int fromLevel, int toLevel);
    
    @LogMessage(level = INFO)
    @Message(id = 11007, value = "Starting bundle due to start level change: %s")
    void infoStartingBundleDueToStartLevel(Bundle bundle);
    
    @LogMessage(level = INFO)
    @Message(id = 11008, value = "Stopping bundle due to start level change: %s")
    void infoStoppingBundleDueToStartLevel(Bundle bundle);
    
    @LogMessage(level = INFO)
    @Message(id = 11009, value = "Starting bundles for start level: %d")
    void infoStartingBundlesForStartLevel(int level);
    
    @LogMessage(level = INFO)
    @Message(id = 11010, value = "Stopping bundles for start level: %d")
    void infoStoppingBundlesForStartLevel(int level);
    
    @LogMessage(level = INFO)
    @Message(id = 11011, value = "No resolvable singleton bundle: %s")
    void infoNoResolvableSingleton(Bundle bundle);
    
    @LogMessage(level = INFO)
    @Message(id = 11012, value = "Bundle uninstalled: %s")
    void infoBundleUninstalled(Bundle bundle);
    
    @LogMessage(level = WARN)
    @Message(id = 11013, value = "Cannot process metadata from properties: %s")
    void warnCannotProcessMetadataProperties(@Cause Throwable cause, VirtualFile rootFile);
    
    @LogMessage(level = WARN)
    @Message(id = 11014, value = "Error while firing bundle event %s for: %s")
    void warnErrorWhileFiringBundleEvent(@Cause Throwable cause, String eventType, Bundle bundle);
    
    @LogMessage(level = ERROR)
    @Message(id = 11015, value = "Framework Warning")
    void warnFrameworkEvent(@Cause Throwable cause);

    @LogMessage(level = WARN)
    @Message(id = 11016, value = "Error while firing %s event")
    void warnErrorWhileFiringEvent(@Cause Throwable cause, String eventType);
    
    @LogMessage(level = WARN)
    @Message(id = 11017, value = "Error while firing service event %s for: %s")
    void warnErrorWhileFiringServiceEvent(@Cause Throwable cause, String eventType, ServiceReference sref);
    
    @LogMessage(level = WARN)
    @Message(id = 11018, value = "Error while calling event hook: %s")
    void warnErrorWhileCallingEventHook(@Cause Throwable cause, EventHook hook);
    
    @LogMessage(level = WARN)
    @Message(id = 11019, value = "Error while calling find hook: %s")
    void warnErrorWhileCallingFindHook(@Cause Throwable cause, FindHook hook);
    
    @LogMessage(level = ERROR)
    @Message(id = 11020, value = "Cannot acquire uninstall lock for: %s")
    void errorCannotAquireUninstallLock(Bundle bundle);
    
    @LogMessage(level = ERROR)
    @Message(id = 11021, value = "Cannot delete storage area")
    void errorCannotDeleteStorageArea(@Cause Throwable cause);
    
    @LogMessage(level = ERROR)
    @Message(id = 11023, value = "Cannot write persistent storage: %s")
    void errorCannotWritePersistentStorage(@Cause Throwable cause, File bundleDir);
    
    @LogMessage(level = ERROR)
    @Message(id = 11024, value = "Cannot start persistent bundle: %s")
    void errorCannotAutomaticallyStartBundle(@Cause Throwable cause, Bundle bundle);
    
    @LogMessage(level = ERROR)
    @Message(id = 11025, value = "Invalid beginning start level: %s")
    void errorInvalidBeginningStartLevel(String levelSpec);
    
    @LogMessage(level = ERROR)
    @Message(id = 11026, value = "Error processing service listener hook: %s")
    void errorProcessingServiceListenerHook(@Cause Throwable cause, ListenerHook hook);
    
    @LogMessage(level = ERROR)
    @Message(id = 11027, value = "Framework Error")
    void errorFrameworkEvent(@Cause Throwable cause);
    
    @LogMessage(level = ERROR)
    @Message(id = 11028, value = "Cannot update framework")
    void errorCannotUpdateFramework(@Cause Throwable cause);
    
    @LogMessage(level = ERROR)
    @Message(id = 11029, value = "Cannot get resources '%s' from: %s")
    void errorCannotGetResources(@Cause Throwable cause, String path, BundleRevision brev);
    
    @LogMessage(level = ERROR)
    @Message(id = 11030, value = "Cannot activate bundle lazily: %s")
    void errorCannotActivateBundleLazily(@Cause Throwable cause, Bundle bundle);
    
    @LogMessage(level = ERROR)
    @Message(id = 11031, value = "Cannot provide native library location for: %s")
    void errorCannotProvideNativeLibraryLocation(@Cause Throwable cause, String libname);
    
    @LogMessage(level = ERROR)
    @Message(id = 11032, value = "Cannot install persistet bundle from: %s")
    void errorCannotInstallPersistentBundlle(@Cause Throwable cause, BundleStorageState storageState);
    
    @LogMessage(level = ERROR)
    @Message(id = 11033, value = "Cannot start persistet bundle: %s")
    void errorCannotStartPersistentBundle(@Cause Throwable cause, Bundle bundle);
    
    @LogMessage(level = ERROR)
    @Message(id = 11034, value = "Cannot get entry '%s' from: %s")
    void errorCannotGetEntry(@Cause Throwable cause, String path, BundleRevision brev);

    @LogMessage(level = ERROR)
    @Message(id = 11035, value = "Cannot obtain class loader for: %s")
    void errorCannotObtainClassLoader(@Cause Throwable cause, BundleRevision brev);

    @LogMessage(level = ERROR)
    @Message(id = 11036, value = "Cannot remove service: %s")
    void errorCannotRemoveService(@Cause Throwable cause, ServiceName serviceName);

    @LogMessage(level = ERROR)
    @Message(id = 11037, value = "Service interface [%s] loaded from [%s] is not assignable from [%s] loaded from [%s]")
    void errorServiceNotAssignable(String name, ClassLoader loader, String otherName, ClassLoader otherLoader);

    @LogMessage(level = ERROR)
    @Message(id = 11038, value = "Cannot load [%s] from: %s")
    void errorCannotLoadService(String className, Bundle bundle);

    @LogMessage(level = ERROR)
    @Message(id = 11039, value = "Cannot get class path entry '%s' from: %s")
    void errorCannotGetClassPathEntry(@Cause Throwable cause, String path, BundleRevision brev);
}
