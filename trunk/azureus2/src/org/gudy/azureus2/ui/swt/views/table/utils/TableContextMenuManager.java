/*
 * File    : TableContextMenuManager.java
 * Created : 2004/May/16
 *
 * Azureus - a Java Bittorrent client
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
 */
 
package org.gudy.azureus2.ui.swt.views.table.utils;

import java.util.*;
import org.gudy.azureus2.plugins.ui.tables.*;


public class TableContextMenuManager {
  private static TableContextMenuManager instance;

  /* Holds all the TableContextMenu objects.
   * key   = TABLE_* type (see TableColumn)
   * value = Map:
   *           key = context menu key
   *           value = TableContextMenu object
   */
  private Map items;

  private TableContextMenuManager() {
   items = new HashMap();
  }

  /** Retrieve the static TableContextMenuManager instance
   * @return the static TableContextMenuManager instance
   */
  public static synchronized TableContextMenuManager getInstance() {
    if (instance == null)
      instance = new TableContextMenuManager();
    return instance;
  }

  public void addContextMenuItem(TableContextMenuItem item) {
    try {
      String name = item.getResourceKey();
      String sTableID = item.getTableID();
      synchronized(items) {
        Map mTypes = (Map)items.get(sTableID);
        if (mTypes == null) {
          // LinkedHashMap to preserve order
          mTypes = new LinkedHashMap();
          items.put(sTableID, mTypes);
        }
        mTypes.put(name, item);
      }
    } catch (Exception e) {
      System.out.println("Error while adding Context Table Menu Item");
      e.printStackTrace();
    }
  }

  public TableContextMenuItem[] getAllAsArray(String sTableID) {
    Map mTypes = (Map)items.get(sTableID);
    if (mTypes != null) {
      ArrayList l = new ArrayList();
      l.addAll(mTypes.values());
      // Add global table context menu items
      mTypes = (Map)items.get(null);
      if (mTypes != null)
        l.addAll(mTypes.values());
      return (TableContextMenuItem[])l.toArray(new TableContextMenuItem[0]);
    }
    return new TableContextMenuItem[0];
  }
}