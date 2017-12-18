package com.sciome.filter;

import java.util.ArrayList;
import java.util.List;

import com.sciome.bmdexpress2.mvp.model.BMDExpressAnalysisRow;

/*
 * Data Filter package that contains a list of filters.  It also has method to 
 * see if an object passes the filters
 */
public class DataFilterPack
{

	private String				name;
	private List<DataFilter>	dataFilters;

	public DataFilterPack()
	{

	}

	public DataFilterPack(String name, List<DataFilter> dataFilters)
	{
		super();
		this.name = name;
		this.dataFilters = dataFilters;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public List<DataFilter> getDataFilters()
	{
		return dataFilters;
	}

	public void setDataFilters(List<DataFilter> dataFilters)
	{
		this.dataFilters = dataFilters;
	}

	/*
	 * take this object and see if it passes the filter
	 */
	public boolean passesFilter(BMDExpressAnalysisRow record)
	{
		if (dataFilters.isEmpty())
			return true;
		if (dataFilters == null)
			return false;
		for (DataFilter df : dataFilters)
		{
			if (!df.passesFilter(record))
				return false;
		}

		return true;
	}

	public DataFilterPack copy()
	{
		List<DataFilter> dfCopies = new ArrayList<>();
		DataFilterPack dp = new DataFilterPack();
		dp.setName(this.getName());
		if (this.getDataFilters() != null)
			for (DataFilter df : this.getDataFilters())
				dfCopies.add(df.copy());

		dp.setDataFilters(dfCopies);

		return dp;
	}

}
