package tools.logging;

import tools.database.NameColumnsPowerPlant;
import tools.types.Unit;

/**
 * Utility class for adding column headers to xlxs logging files.
 * 
 * @author 
 *
 */
public class ColumnHeader {

	private String columnTitle;
	private Unit columnUnit;

	public ColumnHeader(NameColumnsPowerPlant nameColumnsPowerPlant) {
		columnTitle = nameColumnsPowerPlant.getColumnName();
		columnUnit = nameColumnsPowerPlant.getColumnUnit();
	}

	public ColumnHeader(String columnTitle, Unit columnUnit) {
		this.columnTitle = columnTitle;
		this.columnUnit = columnUnit;
	}

	public String getColumnTitle() {
		return columnTitle;
	}

	public String getColumnUnit() {
		return columnUnit.getUnit();
	}

	@Override
	public String toString() {
		return columnTitle + ";" + columnUnit;
	}

}