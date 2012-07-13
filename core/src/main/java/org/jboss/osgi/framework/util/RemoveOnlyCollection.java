/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.osgi.framework.util;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Collection;
import java.util.Iterator;

/**
 * A Collection that does not allow add operations.
 * 
 * @author thomas.diesler@jboss.com
 * @since 21-MAr-2010
 */
@SuppressWarnings("rawtypes")
public class RemoveOnlyCollection<T> implements Collection<T> {

    Collection<T> delegate;

    public RemoveOnlyCollection(Collection<T> delegate) {
        if (delegate == null)
            throw MESSAGES.illegalArgumentNull("delegate");
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object[] toArray(Object[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(Object e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection c) {
        return delegate.removeAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
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