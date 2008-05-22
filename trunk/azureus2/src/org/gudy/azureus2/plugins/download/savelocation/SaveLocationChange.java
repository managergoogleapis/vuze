/*
 * Created on 12 May 2008
 * Created by Allan Crooks
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package org.gudy.azureus2.plugins.download.savelocation;

import java.io.File;

/**
 * Used by {@link SaveLocationManager} - you create an instance, set the
 * attributes here and return the value.
 * 
 * @since 3.0.5.3
 */
public class SaveLocationChange {
	
	/**
	 * The new location to move the download to.
	 */
	public File download_location = null;

	/**
	 * The new name to give the download.
	 */
	public String download_name = null;
	
	/**
	 * The new location to move the torrent to.
	 */
	public File torrent_location = null;
	
	/**
	 * The new name to give the torrent.
	 */
	public String torrent_name = null;
	
	/**
	 * String representation of this object.
	 */
	public final String toString() {
		StringBuffer res = new StringBuffer("SaveLocationChange: ");
		res.append("DL-LOC=");
		res.append(download_location);
		res.append(", DL-NAME=");
		res.append(download_name);
		res.append(", TOR-LOC=");
		res.append(torrent_location);
		res.append(", TOR-NAME=");
		res.append(torrent_name);
		return res.toString();
	}
	
	/**
	 * Given the location of the existing torrent, determine the new path
	 * to store the torrent.
	 */
	public final File normaliseTorrentLocation(File old_torrent_location) {
		return this.normaliseTorrentLocation(old_torrent_location.getParentFile(), old_torrent_location.getName());
	}

	/**
	 * Given the location of the existing torrent, determine the new path
	 * to store the torrent.
	 */
	public final File normaliseTorrentLocation(File old_torrent_directory, String old_torrent_name) {
		return new File(
			(torrent_location != null) ? torrent_location : old_torrent_directory,
			(torrent_name != null) ? torrent_name : old_torrent_name
		);
	}
	
	/**
	 * Given the location of the existing download, determine the new path
	 * to store the download.
	 */
	public final File normaliseDownloadLocation(File old_download_location) {
		return this.normaliseDownloadLocation(old_download_location.getParentFile(), old_download_location.getName());
	}

	/**
	 * Given the location of the existing download, determine the new path
	 * to store the download.
	 */
	public final File normaliseDownloadLocation(File old_download_directory, String old_download_name) {
		return new File(
			(download_location != null) ? download_location : old_download_directory,
			(download_name != null) ? download_name : old_download_name
		);
	}

	/**
	 * Returns <tt>true</tt> if this object indicates a new location for
	 * a download.
	 */
	public final boolean hasDownloadChange() {
		return this.download_location != null || this.download_name != null;
	}

	/**
	 * Returns <tt>true</tt> if this object indicates a new location for
	 * a torrent.
	 */
	public final boolean hasTorrentChange() {
		return this.torrent_location != null || this.torrent_name != null;
	}
	
}
