/*
 * File    : SortableTableItem.java
 * Created : 23 nov. 2003
 * By      : Olivier
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
 
package org.gudy.azureus2.ui.swt.views.utils;

import org.eclipse.swt.widgets.TableItem;

/**
 * @author Olivier
 *
 * @deprecated Sorting now uses Comparable
 * XXX DeleteMe
 */
public interface SortableItem {
  
  public boolean setDataSource(Object dataSource);
  public TableItem getTableItem();
  public int getIndex();
  
  public void invalidate();
  
  public long getIntField(String field);
  public String getStringField(String field);
}
