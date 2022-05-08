<?php
/*
Mod config in-game.

classname=com.friya.wurmonline.server.pmk.Mod
classpath=friyazenathpmk.jar
#updateStatusUrl=http://www.filterbubbles.com/zenath/pmk.php
#updateStatusUrl=http://zenath.localhost.providi.nl/update.php
updateStatusUrl=http://zenath.net/kingdoms/update.php
updateStatusSecret=978234lksw892jnasdiufy87zzafhzbxcznjuloiiyhjk
*/

require_once("lib/renderer.php");

/**
 * Index page.
 * - Friya @ Zenath, 2017
 */
final class Index extends Renderer
{
	public function __construct()
	{
		$this->render("index");
	}


	protected function getCurrentCoresStatus()
	{
		// We allow only four cores on the server, show the last 6 destroyed cores, but not more (10 total).
		$sql = "SELECT json, MAX(updatetime) FROM coretracking GROUP BY itemid ORDER BY updateTime DESC LIMIT 10";
		$cores = $this->getDecodedResultArray($sql);

		return $cores;
	}


	protected function isAtLegalLocation($core)
	{
		return $core->isOnToken === true && $core->surfaceStatus === true && $core->vehicle == "";
	}
}

new Index();