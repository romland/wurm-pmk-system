<?php
require_once("lib/db.php");

class Update extends DB
{
	public function __construct()
	{
		$requestData = $this->getRequestData();
		$this->secure($requestData);

		$this->openDB();

		// TODO: Have a check for this, no need to do it every time we get an update.
		$this->createOrUpgradeTables();

		// Insert the actual data we just received from PMK mod
		$res = $this->insertCores($this->decode($requestData));

		// Outputs number of cores that were inserted (this is the only thing this page should output)
		echo $res;
	}


	private function getRequestData()
	{
		if(sizeof($_POST) > 0 && $_POST["secret"]) {
			$args = $_POST;
		} else {
			$args = $_GET;
		}
		return $args;	
	}


	private function secure($args)
	{
		if(sizeof($args) === 0 || $args["secret"] == null || $args["secret"] != "978234lksw892jnasdiufy87zzafhzbxcznjuloiiyhjk") {
			die("access denied");
		}

		if(!$args["data"]) {
			die("missing argument");
		}
	}


	private function decode($args)
	{
		if($args["data"] == "test") {
			$data = '{"cores":[{"localNames":"[Friya]","lastPolled":1489737173,"homeCoordinate":"1354 x 439","coordinate":"1354 x 442","vehicleOwner":"","creationTime":1489558718,"isOnHostileToken":false,"distanceFromHome":3,"isDestroyed":false,"serverName":"Friya","lastSeenAtHome":1489737173,"kingdom":"Freedom Isles","surfaceStatus":true,"vehicle":"","itemId":12610349990658,"spawner":"Friya","homeKingdom":"Freedom Isles","name":"kingdom core - Freedom Isles","lastMovedBy":"","homeStatus":false,"village":"New","isOnToken":false}],"dataVersion":1}';
		} else {
			// Well, this is a bit fugly, but the Java side insist on escaping quotes improperly.
			$data = str_replace("\\", "", $args["data"]);
		}

		$data = json_decode($data);

		if(!$data) {
			die("missing data");
		}

		return $data;
	}


	private function insertCores($data)
	{
		$i = 0;

		foreach($data->cores as $core) {
			if($this->insertCore($core)) {
				$i++;
			}
		}

		return $i;
	}


	private function insertCore($core)
	{
		$sql = "INSERT INTO coretracking (itemid, json, servername, updatetime) VALUES(:itemid, :json, :servername, :updatetime)";
		
		$stmt = $this->prepare($sql);
		$stmt->bindValue(":itemid", $core->itemId);
		$stmt->bindValue(":json", json_encode($core));
		$stmt->bindValue(":updatetime", $core->lastPolled);
		$stmt->bindValue(":servername", $core->serverName);
		$res = $stmt->execute();
		
		if(!$res) {
			//throw new Exception("Failed to insert core: " . $this->lastErrorMsg());
			return false;
		}

		return true;
	}
}

new Update();
