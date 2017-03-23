package kaonbot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Game;
import bwapi.Unit;
import bwta.BaseLocation;
import bwta.Polygon;


public class ScoutManager extends AbstractManager {

	ArrayList<BaseScout> scouts = new ArrayList<BaseScout>();
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
	
	private class BaseScout extends Behavior{

		BaseLocation toScout;
		int polygonIndex = 0;
		final int MICRO_LOCK = 5;
		boolean foundBase = false;
		
		public BaseScout(Claim unit, BaseLocation base) {
			super(unit);
			toScout = base;
		}

		@Override
		public boolean update() {
			if(KaonBot.getGame().getFrameCount() % MICRO_LOCK != 0){
				return false;
			}
			
			Polygon poly = toScout.getRegion().getPolygon();
			
			if(!foundBase && poly.isInside(getUnit().getPosition())){
				foundBase = true;
				polygonIndex = poly.getPoints().indexOf(poly.getNearestPoint(getUnit().getPosition()));
			} else {
				getUnit().move(toScout.getPosition());
			}
			
			if(foundBase){
				if(poly.getNearestPoint(getUnit().getPosition()) == poly.getPoints().get(polygonIndex)){
					polygonIndex++;
				}
				getUnit().move(poly.getPoints().get(polygonIndex));
			}
			return false;
		}
		
	}

}
