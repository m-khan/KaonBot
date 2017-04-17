package kaonbot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Game;
import bwapi.Position;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;

public class DetectionManager extends AbstractManager {

	private Unit academy = null;
	private Unit ebay = null;
	private boolean hasRax = false;
	private Map<Unit, Integer> bases = new HashMap<Unit, Integer>();
	private ArrayList<Unit> comsats = new ArrayList<Unit>();
	private static final double FIRST_CLOAK_BONUS = 30;
	private static final int FLOAT_PASSIVE_TICK_THRESHOLD = 500;
	private static final double TICK_MULT = 0.0001;
	private static final double TURRET_DECREMENT = -0.5;
	private static final double COMSAT_DECREMENT = -1.0;
	private static final double FAILED_SCAN = 1.0;
	private boolean firstCloak = false;
	private static final int SCAN_COOLDOWN = 30;
	private int scanCooldown = 0;
//	private Unit sciFac = null;
	private static final double TURRET_PRIORITY = 0.5;
	
	public DetectionManager(double baselinePriority, double volitilityScore) {
		super(baselinePriority, volitilityScore);
	}

	@Override
	public void init(Game game) {
		
	}

	@Override
	public String getName(){
		String status = "";
		if(academy != null){
			status += "A";
		}
		if(ebay != null){
			status += "E";
		}
		
		return "DETECTION " + status;
	}

	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		if(enemy && unit.isCloaked()){
			requestScan(unit.getPosition());
			if(firstCloak == false){
				incrementPriority(getVolitility() * FIRST_CLOAK_BONUS, true);
				firstCloak = true;
			} else {
				incrementPriority(getVolitility(), false);
			}
		} else if(friendly && unit.getType() == UnitType.Terran_Missile_Turret){
			incrementPriority(getVolitility() * TURRET_DECREMENT, false);
		} else if(friendly && unit.getType() == UnitType.Terran_Comsat_Station){
			comsats.add(unit);
			KaonBot.print("COMSAT ADDED", true);
			incrementPriority(getVolitility() * COMSAT_DECREMENT, false);
		}
	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		if(friendly){
			if(u.getType() == UnitType.Terran_Missile_Turret){
				incrementPriority(getVolitility() * TURRET_DECREMENT * -1, false);
			}
			
			if(scanCooldown == 0){
				for(Unit unit: u.getUnitsInRadius(100)){
					if(unit.isCloaked() || unit.isBurrowed()){
						if(requestScan(unit.getPosition())){
							scanCooldown = SCAN_COOLDOWN;
						}
					}
				}
			}
		}
	}

	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		UnitType type = unit.getType();
		
		if(friendly){
			if (type.isResourceDepot()){
				bases.put(unit, 0); //new DetectionBase(unit));
			} else if (type == UnitType.Terran_Barracks){
				hasRax = true;
			} else if (type == UnitType.Terran_Academy){
				academy = unit;
			} else if (type == UnitType.Terran_Engineering_Bay){
				ebay = unit;
			} else if (type == UnitType.Terran_Missile_Turret) {
				for(Unit cc: bases.keySet()){
					if(BWTA.getNearestBaseLocation(cc.getPosition()) == BWTA.getNearestBaseLocation(unit.getPosition())){
						bases.put(cc, bases.get(cc) + 1);
					}
				}
			}
		}
	}

	public boolean requestScan(Position position){
		for(Unit comsat: comsats){
			if(comsat.canUseTechPosition(TechType.Scanner_Sweep, position)){
				KaonBot.print("SCANNING " + position);
				comsat.useTech(TechType.Scanner_Sweep, position);
				return true;
			}
		}
		KaonBot.print("NO SCANS AVAILABLE TO SCAN " + position);
		incrementPriority(getVolitility() * FAILED_SCAN, true);
		return false;
	}
	
	@Override
	public void runFrame() {
		if(scanCooldown > 0) scanCooldown--;
		if(KaonBot.getGas() > FLOAT_PASSIVE_TICK_THRESHOLD && KaonBot.getMinerals() > FLOAT_PASSIVE_TICK_THRESHOLD){
			incrementPriority(getVolitility() * TICK_MULT, false);
		}
	}

	@Override
	public List<ProductionOrder> getProductionRequests() {
		ArrayList<ProductionOrder> prodList = new ArrayList<ProductionOrder>();

		if(hasRax){
			if(academy == null){
				if(ProductionQueue.numActiveOrders(UnitType.Terran_Academy) == 0){
					prodList.add(new BuildingOrder(150, 0, this.usePriority(), UnitType.Terran_Academy, null));
				}
			} else {
				if(!academy.exists()){
					academy = null;
				} else {
					for(Unit cc: bases.keySet()){
						if(cc.getAddon() == null){
							prodList.add(new AddonOrder(50, 50,this.usePriority(), cc, UnitType.Terran_Comsat_Station));
						}
					}
				}
			}
		}
		
		if(ebay == null){
			if(ProductionQueue.numActiveOrders(UnitType.Terran_Engineering_Bay) == 0){
				prodList.add(new BuildingOrder(150, 0, this.usePriority() * TURRET_PRIORITY, UnitType.Terran_Engineering_Bay, null));
			}
		} else {
			if(!ebay.exists()){
				ebay = null;
			} else {
				if(ProductionQueue.numActiveOrders(UnitType.Terran_Missile_Turret) == 0)
				{
					for(Unit cc: bases.keySet()){
						TilePosition buildLocation = BuildingPlacer.getInstance().getBuildTile(UnitType.Terran_Missile_Turret, cc.getTilePosition());
						prodList.add(new BuildingOrder(75, 0, this.usePriority() * TURRET_PRIORITY / (bases.get(cc) + 1), UnitType.Terran_Missile_Turret, buildLocation));
					}
				}
			}
		}
				
		return prodList;
	}

	@Override
	public void assignNewUnitBehaviors() {
	}

	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		return null;
	}

	@Override
	protected void addCommandeerCleanup(Claim claim) {
	}
	
}
