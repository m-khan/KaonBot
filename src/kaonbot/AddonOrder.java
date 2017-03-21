package kaonbot;
import java.util.Comparator;

import bwapi.Unit;
import bwapi.UnitType;

public class AddonOrder extends ProductionOrder implements Comparator<ProductionOrder>{
	
	private Unit producer;
	private UnitType toProduce;
	
	public AddonOrder(int minerals, int gas, double priority, Unit producer, UnitType toProduce){
		super(ProductionOrder.UNIT, minerals, gas, priority);
	}
	
	public boolean execute(){
		executed = producer.buildAddon(toProduce);
		return executed;
	}
	
	public boolean canExecute(){
		return producer.exists();
	}
}
