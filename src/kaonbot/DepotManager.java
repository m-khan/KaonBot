package kaonbot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Color;
import bwapi.Game;
import bwapi.Player;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;


public class DepotManager extends AbstractManager {

	EconomyManager econ;
	Player player;
	TilePosition nextDepot = null;
	TilePosition depotBase;
	Map<Integer, Unit> depotList = new HashMap<Integer, Unit>();
	int frameCount = 0;
	final int FRAME_LOCK = 50;
	final int NUM_DEPOTS_TO_QUEUE = 5;
	
	public DepotManager(double baselinePriority, double volatilityScore, EconomyManager econ, Player player) {
		super(baselinePriority, volatilityScore);
		this.econ = econ;
		this.player = player;
		depotBase = player.getStartLocation();
	}

	@Override
	public void init(Game game) {
	}

	@Override
	public String getName(){
		int supply = player.supplyTotal();
		for(Integer i: depotList.keySet()){
			if(depotList.get(i).isConstructing()){
				supply += 8;
			}
		}
		
		int used = player.supplyUsed();

		return "DEPOTS " + used + "/" + supply;
	}
	
	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		if(friendly && unit.getType() == UnitType.Terran_Supply_Depot)
		{
			depotList.put(unit.getID(), unit);
		}
	}

	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
	}

	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		return null;
	}

	@Override
	public void runFrame() {
		if(frameCount > 0){
			frameCount -= 1;
			return;
		}
		
		int supply = player.supplyTotal();
		for(Integer i: depotList.keySet()){
			if(depotList.get(i).isConstructing() && depotList.get(i).getBuildUnit() != null){
				supply += 8;
			}
		}

		if(supply <= player.supplyUsed()){
			if(player.minerals() > 100){
				incrementPriority(getVolitility() * player.minerals() / 10, false);
			}
			incrementPriority(getVolitility(), false);
		}
		
		findNextDepotSpot();
		frameCount = FRAME_LOCK;
	}

	private double getDepotPriority(){
		int supply = player.supplyTotal();
		
		for(Integer i: depotList.keySet()){
			if(depotList.get(i).isConstructing()){
				supply += 8;
			}
		}

		if(supply >= 400) return 0;

		int used = player.supplyUsed();

		double multiplier = 1.0 - ((supply - used) / 16.0);
		return this.usePriority(multiplier);
	}
	
	private void findNextDepotSpot(){
		Unit builder = BuildingPlacer.getInstance().getSuitableBuilder(player.getStartLocation(), getDepotPriority(), this);
		if(builder != null){
			try{
				nextDepot = BuildingPlacer.getInstance().getBuildTile(builder, UnitType.Terran_Supply_Depot, KaonBot.mainPosition.getTilePosition());
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
		
	@Override
	public List<ProductionOrder> getProductionRequests() {
		List<ProductionOrder> toReturn = new ArrayList<ProductionOrder>();
		double depotPriority = getDepotPriority();
		int depotsToQueue = NUM_DEPOTS_TO_QUEUE;
		
		if(nextDepot == null){
			return toReturn;
		}
		for(BuildingOrder o: ProductionQueue.getActiveOrders()){
			if(o.getUnitType() == UnitType.Terran_Supply_Depot){
				depotsToQueue -= 1;
				depotPriority = depotPriority / 2;
			}
		}
		
		if(depotsToQueue > 0){
			toReturn.add(new BuildingOrder(100, 0, depotPriority, 
					UnitType.Terran_Supply_Depot, nextDepot));
		}

		return toReturn;
	}

	@Override
	public void assignNewUnitBehaviors() {
	}

	@Override
	protected void addCommandeerCleanup(Claim claim) {
	}

	@Override
	public void displayDebugGraphics(Game game){
		if(nextDepot != null){
			game.drawCircleMap(nextDepot.toPosition(), frameCount, debugColor);
		}
	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		// TODO Auto-generated method stub
		
	}
}
