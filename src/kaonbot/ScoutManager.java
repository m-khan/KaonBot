package kaonbot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Game;
import bwapi.Unit;


public class ScoutManager extends AbstractManager {

	ArrayList<Scout> scouts = new ArrayList<Scout>();
	Map<Integer, Unit> enemies = new HashMap<Integer, Unit>();
	
	public ScoutManager(double baselinePriority, double volatility) {
		super(baselinePriority, volatility);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void init(Game game) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		// TODO Auto-generated method stub

	}

	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void runFrame() {
		// TODO Auto-generated method stub

	}

	@Override
	public List<ProductionOrder> getProductionRequests() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void assignNewUnitBehaviors() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void addCommandeerCleanup(Claim claim) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		// TODO Auto-generated method stub
		
	}
	
	private class Scout extends Behavior{

		public Scout(Claim unit) {
			super(unit);

		}

		@Override
		public boolean update() {

			return false;
		}
		
	}

}
