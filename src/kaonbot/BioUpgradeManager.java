package kaonbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bwapi.Game;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;

public class BioUpgradeManager extends AbstractManager {

	private static final int GAS_THRESHOLD = 100;
	private Unit academy = null;
	private Unit ebay = null;
	private int attackLevel = 0;
	private int armorLevel = 0;
//	private Unit sciFac = null;
	private static final double EBAY_PRIORITY = 0.9;
	private final boolean ATTACK_FIRST;
	private ArrayList<Integer> upgradePrices = new ArrayList<Integer>(Arrays.asList(100, 175, 250));
	
	public BioUpgradeManager(double baselinePriority, double volitilityScore) {
		super(baselinePriority, volitilityScore);
		ATTACK_FIRST = true; //KaonUtils.getRandom().nextBoolean();
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
			status += "E" + attackLevel + "/" + armorLevel;
		}
		
		return "BIOUPGRADES " + status;
	}

	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		UnitType type = unit.getType();
		
		if(friendly){
			if(type.isOrganic() && KaonBot.getGas() > GAS_THRESHOLD){
				incrementPriority(getVolitility(), false);
			}
		}
	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		UnitType type = unit.getType();
		if(friendly){
			if (type == UnitType.Terran_Academy){
				academy = unit;
			} else if (type == UnitType.Terran_Engineering_Bay){
				ebay = unit;
			} 
		}
	}

	@Override
	public void runFrame() {
		// TODO Auto-generated method stub

	}

	@Override
	public List<ProductionOrder> getProductionRequests() {
		ArrayList<ProductionOrder> prodList = new ArrayList<ProductionOrder>();
		
		if(KaonBot.getGas() < GAS_THRESHOLD){
			return prodList; 
		}

		// academy tech takes priority
		if(academy == null){
			if(ProductionQueue.numActiveOrders(UnitType.Terran_Academy) == 0){
				prodList.add(new BuildingOrder(150, 0, this.usePriority(), UnitType.Terran_Academy, null));
			}
		} else {
			if(!academy.exists()){
				academy = null;
			} else if(!KaonBot.hasResearched(TechType.Stim_Packs) && KaonBot.getGas() > 100){
				prodList.add(new ResearchOrder(100, 100, this.usePriority() + 0.001, academy, TechType.Stim_Packs));
			} else if(academy.canUpgrade(UpgradeType.U_238_Shells)){
				prodList.add(new UpgradeOrder(150, 100, this.usePriority() + 0.001, academy, UpgradeType.U_238_Shells));
			}
		}

		if(ebay == null){
			if(ProductionQueue.numActiveOrders(UnitType.Terran_Engineering_Bay) == 0){
				prodList.add(new BuildingOrder(150, 0, this.usePriority(), UnitType.Terran_Engineering_Bay, null));
			}
		} else {
			if(!ebay.exists()){
				ebay = null;
			} else {
				
				boolean queueAttack = ATTACK_FIRST;
				attackLevel = KaonBot.getUpgradeLevel(UpgradeType.Terran_Infantry_Weapons);
				armorLevel = KaonBot.getUpgradeLevel(UpgradeType.Terran_Infantry_Armor);
				
				// TODO: Upgrades that require ScienceFacility
				if(attackLevel == 1 && armorLevel == 1){
					return prodList;
				}
				
//				if(attackLevel > 0 && armorLevel > 0 && sciFac == null){
//					if(ProductionQueue.numActiveOrders(UnitType.Terran_Science_Facility) == 0){
//						prodList.add(new BuildingOrder(100, 150, this.usePriority(), UnitType.Terran_Science_Facility, null));
//					}
//				}
				
				if(attackLevel > armorLevel){
					queueAttack = false;
				} else if (attackLevel < armorLevel) {
					queueAttack = true;
				}
				
				if(queueAttack && attackLevel < 1 && ebay.canUpgrade(UpgradeType.Terran_Infantry_Weapons)){
					int price = upgradePrices.get(attackLevel);
					prodList.add(new UpgradeOrder(price, price, this.usePriority() * EBAY_PRIORITY / attackLevel, ebay, UpgradeType.Terran_Infantry_Weapons));
				} else if (!queueAttack && armorLevel < 1 && ebay.canUpgrade(UpgradeType.Terran_Infantry_Armor)){
					int price = upgradePrices.get(armorLevel);
					prodList.add(new UpgradeOrder(price, price, this.usePriority() * EBAY_PRIORITY / attackLevel, ebay, UpgradeType.Terran_Infantry_Armor));
				}
			}
		}
				
		return prodList;
	}

	@Override
	public void assignNewUnitBehaviors() {
		// TODO Auto-generated method stub

	}

	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void addCommandeerCleanup(Claim claim) {
		// TODO Auto-generated method stub

	}

}
