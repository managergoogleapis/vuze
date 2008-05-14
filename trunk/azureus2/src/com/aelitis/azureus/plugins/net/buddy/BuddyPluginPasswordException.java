/*
 * Created on May 2, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.net.buddy;


public class 
BuddyPluginPasswordException
	extends BuddyPluginException
{
	
	private static final long serialVersionUID = -1L;
	private boolean 	was_incorrecte;
	
	public 
	BuddyPluginPasswordException(
		boolean		_was_incorrecte,
		String		str,
		Throwable	cause )
	{
		super( str, cause );
		
		was_incorrecte = _was_incorrecte;
	}
	
	public boolean
	wasIncorrect()
	{
		return( was_incorrecte );
	}
}
