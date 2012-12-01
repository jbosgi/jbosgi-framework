package org.jboss.osgi.framework.internal;
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

import java.util.Dictionary;
import java.util.Map;

import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;

/**
 * Dummy filter implementation
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author Thomas.Diesler@jboss.com
 */
public class NoFilter implements Filter {

    /** Singleton instance */
    public static final Filter INSTANCE = new NoFilter();

    private NoFilter() {
    }

    @SuppressWarnings("rawtypes")
    public boolean match(Dictionary dictionary) {
        return true;
    }

    public boolean match(ServiceReference<?> reference) {
        return true;
    }

    @SuppressWarnings("rawtypes")
    public boolean matchCase(Dictionary dictionary) {
        return true;
    }
    
    @Override
    public boolean matches(Map<String, ?> map) {
        // [TODO] R5 Filter.matches 
        throw new UnsupportedOperationException();
    }
}
