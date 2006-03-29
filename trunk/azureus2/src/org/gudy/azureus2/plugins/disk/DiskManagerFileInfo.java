/*
 * Created : 2004/May/26
 *
 * Copyright (C) 2004, 2005, 2006 Aelitis SAS, All rights Reserved
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

package org.gudy.azureus2.plugins.disk;

import java.io.File;

import org.gudy.azureus2.plugins.download.*;

/**
 * @author TuxPaper
 *
 * @since 2.1.0.0
 */

public interface 
DiskManagerFileInfo 
{
	public static final int READ = 1;
	public static final int WRITE = 2;

		// set methods
		
	public void 
	setPriority(
		boolean b );
	
	public void 
	setSkipped(
		boolean b );
	
		/**
		 * Mark the file as deleted or not (deleted means the file will be truncated to take up minimum
		 * space. This is generally 0 <= X < 2*piece_length as pieces can span file boundaries
		 * @since 2403
		 * @param b
		 */
	
	public void
	setDeleted(boolean b);
	
		// links the file to the named destination
	
	public void
	setLink(
		File	link_destination );
	
		// gets the current link, null if none
	
	public File
	getLink();
	
	 	// get methods
	 	
	public int 
	getAccessMode();
	
	public long 
	getDownloaded();
	
	public long
	getLength();
	
	public File 
	getFile();
		
	public int 
	getFirstPieceNumber();
	
	public int 
	getNumPieces();
		
	public boolean 
	isPriority();
	
	public boolean 
	isSkipped();
	
	public boolean
	isDeleted();
	
	public Download 
	getDownload()
	
         throws DownloadException;
	
	public DiskManagerChannel
	createChannel();
}
