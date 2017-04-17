package kaonbot;
import java.util.Comparator;

import bwapi.Unit;
import bwapi.UnitType;

public class AddonOrder extends UnitOrder implements Comparator<ProductionOrder>{
	
	public AddonOrder(int minerals, int gas, double priority, Unit producer,
			UnitType toProduce) {
		super(minerals, gas, priority, producer, toProduce);
	}
	
	@Override
	public boolean execute(){
		setDone();
		return producer.buildAddon(toProduce);
	}
}
