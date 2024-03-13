/*
 * Copyright (C) 2009 Quadduc <quadduc@gmail.com>
 * 
 * This file is part of LateralGM.
 * LateralGM is free software and comes with ABSOLUTELY NO WARRANTY.
 * See LICENSE for details.
 */

package org.lateralgm.util;

import java.beans.ExceptionListener;

import org.lateralgm.util.PropertyMap.PropertyUpdateEvent;
import org.lateralgm.util.PropertyMap.PropertyUpdateListener;

public abstract class PropertyLink<K extends Enum<K>, V> extends PropertyUpdateListener<K>
	{
	public final PropertyMap<K> map;
	public final K key;
	private ExceptionListener exceptionListener;

	public PropertyLink(PropertyMap<K> m, K k)
		{
		map = m;
		key = k;
		m.getUpdateSource(k).addListener(this);
		}

	public void remove()
		{
		map.updateSource.removeListener(this);
		}

	protected abstract void setComponent(V v);

	protected void reset()
		{
		V v = map.get(key);
		editComponent(v);
		}

	protected void editComponentIfChanged(V old)
		{
		V v = map.get(key);
		if (v == null ? old == null : v.equals(old)) return;
		editComponent(v);
		}

	protected void editComponent(V v)
		{
		setComponent(v);
		}

	protected void editProperty(Object v)
		{
		try
			{
			map.put(key,v);
			}
		catch (RuntimeException re)
			{
			reset();
			if (exceptionListener != null)
				exceptionListener.exceptionThrown(re);
			else
				throw re;
			}
		}

	public void setExceptionListener(ExceptionListener l)
		{
		exceptionListener = l;
		}

	@Override
	public void updated(PropertyUpdateEvent<K> e)
		{
		V v = map.get(key);
		editComponent(v);
		}

	public static void removeAll(PropertyLink<?,?>...links)
		{
		for (PropertyLink<?,?> l : links)
			if (l != null) l.remove();
		}
	}
