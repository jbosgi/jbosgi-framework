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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A Map that does not allow put operations.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Jan-2013
 */
public class RemoveOnlyMap<K,V> implements Map<K,V> {

    Map<K,V> delegate;

    public RemoveOnlyMap(Map<K,V> delegate) {
        if (delegate == null)
            throw MESSAGES.illegalArgumentNull("delegate");
        this.delegate = delegate;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean containsKey(Object arg0) {
        return delegate.containsKey(arg0);
    }

    @Override
    public boolean containsValue(Object arg0) {
        return delegate.containsValue(arg0);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public V get(Object arg0) {
        return delegate.get(arg0);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public V put(K arg0, V arg1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> arg0) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object arg0) {
        return delegate.remove(arg0);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Collection<V> values() {
        return delegate.values();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}