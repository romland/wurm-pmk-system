<?php
require_once("lib/renderer.php");

/**
 * History page.
 * - Friya @ Zenath, 2017
 */
final class History extends Renderer
{
	private $coreId = null;


	public function __construct()
	{
		$this->coreId = floatval($_GET["core"]);	// sanitize input

		$this->render("history");
	}


	protected function getCoreId()
	{
		return $this->coreId;
	}


	protected function getHistory($numDays)
	{
		$startTime = $numDays * 24 * 60 * 60;

		// $coreId will always be sanitized here; safe to just concatenate.
		$sql = "SELECT json FROM coretracking WHERE itemid = " . $this->coreId . " AND updatetime > " . $startTime . " ORDER BY updatetime DESC";

		$entries = $this->getDecodedResultArray($sql);

		return $entries;
	}
}

new History();