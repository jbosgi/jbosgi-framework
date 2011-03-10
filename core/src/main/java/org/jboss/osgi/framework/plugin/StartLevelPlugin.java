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
package org.jboss.osgi.framework.plugin;

import org.osgi.service.startlevel.StartLevel;

/**
 * The start level plugin implements the standard OSGi Start Level service and adds synchronous versions for moving the system
 * start level which are used internally.
 * 
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public interface StartLevelPlugin extends ExecutorServicePlugin, StartLevel {

    static final int BUNDLE_STARTLEVEL_UNSPECIFIED = -1;

    /**
     * Increase the start level to the specified level. This method moves to the specified start level in the current thread and
     * returns when the desired start level has been reached.
     * 
     * @param level the target start level.
     */
    void increaseStartLevel(int level);

    /**
     * Decrease the start level to the specified level. This method moves to the specified start level in the current thread and
     * returns when the desired start level has been reached.
     * 
     * @param level the target start level.
     */
    void decreaseStartLevel(int level);
}
