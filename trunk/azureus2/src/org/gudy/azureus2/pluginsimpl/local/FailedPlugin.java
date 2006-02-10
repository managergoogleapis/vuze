/*
 * File    : FailedPlugin.java
 * Created : Dec 2, 2005
 * By      : TuxPaper
 *
 * Copyright (C) 2005, 2006 Aelitis SAS, All rights Reserved
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * AELITIS, SAS au capital de 46,603.30 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

package org.gudy.azureus2.pluginsimpl.local;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.PluginException;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.UnloadablePlugin;

public class FailedPlugin implements UnloadablePlugin {
	protected String plugin_name;

	protected String plugin_dir;

	protected PluginInterfaceImpl plugin_interface;

	public FailedPlugin() {
		plugin_name = null;
		plugin_dir = null;
	}

	public FailedPlugin(String _name, String _target_dir) {
		plugin_name = _name;
		plugin_dir = _target_dir;
	}

	public void initialize(PluginInterface pi) throws PluginException {
		plugin_interface = (PluginInterfaceImpl) pi;

		plugin_interface.setPluginVersion("0.0");

		if (plugin_name == null)
			plugin_interface.setPluginName(plugin_interface.getPluginID());
		else
			plugin_interface.setPluginName(plugin_name);

		if (plugin_dir != null)
			plugin_interface.setPluginDirectoryName(plugin_dir);
	}

	public void unload() {
	}

	/**
	 * For installer
	 */
	public void requestUnload() {
		try {
			plugin_interface.unload();

		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}
}
