<?php

/**
 * DB helper.
 * - Friya @ Zenath, 2017
 */
class DB extends SQLite3
{
	private $opened = false;


	public function __destruct()
	{
		if(!$this->opened) {
			return;
		}

		$this->close();
	}

	// We inherit SQLite3, which will make all DB related methods available in 'this'.
	// There might be name collisions, be careful.
	final protected function openDB()
	{
		// NOTE SECURITY:
		// The database should really live outside of the site directory, but I don't
		// see it as a disaster if that is downloaded. There are no secrets.
		$this->open ( "zenath-pmk.db", SQLITE3_OPEN_READWRITE | SQLITE3_OPEN_CREATE );
		$this->opened = true;
	}


	final protected function createOrUpgradeTables()
	{
		if(!$this->opened) $this->openDB();

		$sql = "CREATE TABLE IF NOT EXISTS coretracking
			(
				id 			INTEGER PRIMARY KEY NOT NULL,
				itemid 		INTEGER NOT NULL,
				json 		VARCHAR(2048) NOT NULL,
				servername 	VARCHAR(255) NOT NULL DEFAULT '',
				updatetime 	INTEGER NOT NULL
			);";

		$res = $this->exec($sql);
		if(!$res) {
			throw new Exception("Failed to create table: " . $this->lastErrorMsg());
		}

		// Upgrade DB.
		if($this->columnExists("coretracking", "servername") === false) {
			$sql = "ALTER TABLE coretracking ADD COLUMN servername 	VARCHAR(255) NOT NULL DEFAULT ''";
			$this->exec($sql);
			echo "Upgraded database";

		}
	}


	// This is call and forget. It will cost more due to iterating over entire result
	// before returning it, it's for convenience. Caller should make sure to not ask
	// for too much data in one go if using this one.
	protected function getResultArray($sql)
	{
		if(!$this->opened) $this->openDB();

		$rs = $this->query($sql);
		$ret = array();
		while($row = $rs->fetchArray(SQLITE3_ASSOC)) {
			$ret[] = $row;
		}

		return $ret;
	}


	// Result of query must include a field called json which will be merged into one
	// array, all other fields are ignored in the resturned result.
	protected function getDecodedResultArray($sql)
	{
		if(!$this->opened) $this->openDB();

		$rs = $this->query($sql);
		$ret = array();
		while($row = $rs->fetchArray(SQLITE3_ASSOC)) {
			$ret[] = json_decode($row["json"]);
		}

		return $ret;
	}


	protected function columnExists($table, $column)
	{
		if(!$this->opened) $this->openDB();

		$sql = "PRAGMA table_info(" . $table . ")";
		$rows = $this->getResultArray($sql);
		foreach($rows as $row) {
			if($row["name"] == $column) {
				return true;
			}
		}

		return false;
	}

}
