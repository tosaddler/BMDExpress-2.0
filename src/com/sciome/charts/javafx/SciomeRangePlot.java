package com.sciome.charts.javafx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sciome.charts.data.ChartConfiguration;
import com.sciome.charts.data.ChartData;
import com.sciome.charts.data.ChartDataPack;
import com.sciome.charts.export.ChartDataExporter;

import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Glow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.util.Duration;

public class SciomeRangePlot extends ScrollableSciomeChart implements ChartDataExporter
{

	// map that keeps track of enough information to instantiate a node.
	// so we don't have to store large amounts of nodes in memory
	private Map<String, NodeInformation>	nodeInfoMap	= new HashMap<>();
	private Tooltip							toolTip		= new Tooltip("");
	private final int						MAXITEMS	= 20;

	@SuppressWarnings("unchecked")
	public SciomeRangePlot(String title, List<ChartDataPack> chartDataPacks, String minKey, String maxKey,
			String lowKey, String highKey, String middleKey, SciomeChartListener chartListener)
	{
		super(title, chartDataPacks, chartListener);

		this.chartDataPacks = chartDataPacks;
		this.addDataAtTop = true;

		chartableKeys = new String[] { minKey, maxKey, lowKey, highKey, middleKey };

		logXAxis.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> ov, Boolean old_val, Boolean new_val)
			{
				initChart();
			}
		});
		lockXAxis.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> ov, Boolean old_val, Boolean new_val)
			{
				initChart();
			}
		});

		showLogAxes(true, false, true, false);
		initChart();
	}

	private void initChart()
	{
		seriesData.clear();
		showChart();
		setMaxGraphItems(MAXITEMS);
		intializeScrollableChart();
	}

	// never show all for this. because it's like a bar chart
	@Override
	public void setShowShowAll(boolean showshowall)
	{
		super.setShowShowAll(false);
	}

	/**
	 *
	 */
	private class RangePlot extends XYChart<Number, String>
	{

		// -------------- CONSTRUCTORS ----------------------------------------------
		/**
		 */
		@SuppressWarnings("unchecked")
		public RangePlot(CategoryAxis yAxis, Axis<Number> xAxis)
		{
			super(xAxis, yAxis);
			// super.setMinSize(900, 900);

			setAnimated(false);
			xAxis.setAnimated(false);
			yAxis.setAnimated(false);

			// initilaize with empty set of data.
			XYChart.Series<Number, String> nullSeries = new XYChart.Series<Number, String>();
			nullSeries.setName("nullname");
			setData(FXCollections.observableArrayList());
			// setData(FXCollections.observableArrayList(nullSeries));
			setLegend(createLegend());
		}

		/**
		 */
		@SuppressWarnings("unused")
		public RangePlot(CategoryAxis yAxis, Axis<Number> xAxis, ObservableList<Series<Number, String>> data)
		{
			this(yAxis, xAxis);
			setData(data);
		}

		private Node createLegend()
		{
			StackPane node = new StackPane();
			node.getStyleClass().setAll("chart-legend");
			VBox vBox = new VBox();
			int seriesIndex = 0;
			if (getData() == null)
				return node;
			for (Series series : getData())
			{

				int colorIndex = (seriesIndex - 1) % 7;
				Region bar = new Region();
				bar.setMinWidth(10.0);
				bar.setMinHeight(10.0);
				bar.setMaxHeight(10.0);
				bar.setMaxWidth(10.0);
				bar.getStyleClass().setAll("boxwhisker-box", "series", "data", "default-color" + colorIndex);
				HBox hBox = new HBox();
				hBox.getChildren().addAll(bar, new Label(series.getName()));
				vBox.getChildren().add(hBox);

				hBox.setAlignment(Pos.CENTER_LEFT);
				hBox.setSpacing(5.0);
				seriesIndex++;
			}
			node.getChildren().add(vBox);

			return node;
		}

		// -------------- METHODS
		// ------------------------------------------------------------------------------------------
		/** Called to update and layout the content for the plot */
		@Override
		protected void layoutPlotChildren()
		{

			// we have nothing to layout if no data is present
			if (getData() == null)
			{
				return;
			}
			// update candle positions
			int seriesCount = getData().size();
			int barCount = 0;
			for (Series series : getData())
			{
				for (Object data : series.getData())
					barCount++;
			}
			for (int seriesIndex = 0; seriesIndex < getData().size(); seriesIndex++)
			{
				Series<Number, String> series = getData().get(seriesIndex);
				Iterator<Data<Number, String>> iter = getDisplayedDataIterator(series);

				while (iter.hasNext())
				{
					Data<Number, String> item = iter.next();
					double x = getXAxis().getDisplayPosition(getCurrentDisplayedXValue(item));
					double y = getYAxis().getDisplayPosition(getCurrentDisplayedYValue(item));
					Node itemNode = item.getNode();
					BoxAndWhiskerExtraValues extra = (BoxAndWhiskerExtraValues) item.getExtraValue();
					if (itemNode instanceof BoxAndWhisker && extra != null)
					{
						BoxAndWhisker boxAndWhisker = (BoxAndWhisker) itemNode;

						Double close = null;

						Double high = null;
						if (extra.getMax() != null)
							high = getXAxis().getDisplayPosition(extra.getMax()) - x;

						Double low = null;
						if (extra.getMin() != null)
							low = getXAxis().getDisplayPosition(extra.getMin()) - x;
						// calculate candle width
						double candleWidth = -1;
						double spacingOffset = 0.0;
						if (getYAxis() instanceof CategoryAxis)
						{
							CategoryAxis xa = (CategoryAxis) getYAxis();
							double scaler = 2 * (double) barCount / getMaxGraphItems();
							if (scaler > .9)
								scaler = .9;
							candleWidth = xa.getCategorySpacing() * scaler / (seriesCount);
							double actualCategorySpacing = candleWidth * (seriesCount);
							spacingOffset = -actualCategorySpacing / 2 + candleWidth / 2;
							// / 2;
							// between ticks
						}
						// update candle
						double theSpacingOffset = 0.0;
						if (seriesCount > 1)
							theSpacingOffset = seriesIndex * candleWidth + spacingOffset;
						boxAndWhisker.update(close, high, low, candleWidth, theSpacingOffset);

						// position the candle
						boxAndWhisker.setLayoutX(x);
						boxAndWhisker.setLayoutY(y);
					}
				}
			}
		}

		@Override
		protected void dataItemChanged(Data<Number, String> item)
		{
		}

		@Override
		protected void dataItemAdded(Series<Number, String> series, int itemIndex, Data<Number, String> item)
		{
			Node candle = createBoxAndWhiskerNode(getData().indexOf(series), item, itemIndex);
			if (shouldAnimate())
			{
				candle.setOpacity(0);
				getPlotChildren().add(candle);
				// fade in new candle
				FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
				ft.setToValue(1);
				ft.play();
			}
			else
			{
				getPlotChildren().add(candle);
			}
			// always draw average line on top
			if (series.getNode() != null)
			{
				series.getNode().toFront();
			}
		}

		@Override
		protected void dataItemRemoved(Data<Number, String> item, Series<Number, String> series)
		{
			final Node candle = item.getNode();
			if (shouldAnimate())
			{
				// fade out old candle
				FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
				ft.setToValue(0);
				ft.setOnFinished(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent actionEvent)
					{
						getPlotChildren().remove(candle);
					}
				});
				ft.play();
			}
			else
			{
				getPlotChildren().remove(candle);
			}
		}

		@SuppressWarnings("rawtypes")
		@Override
		protected void seriesAdded(Series<Number, String> series, int seriesIndex)
		{
			// handle any data already in series
			for (int j = 0; j < series.getData().size(); j++)
			{
				Data item = series.getData().get(j);
				Node candle = createBoxAndWhiskerNode(seriesIndex, item, j);
				if (shouldAnimate())
				{
					candle.setOpacity(0);
					getPlotChildren().add(candle);
					// fade in new candle
					FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
					ft.setToValue(1);
					ft.play();
				}
				else
				{
					getPlotChildren().add(candle);
				}
			}
			// create series path
			Path seriesPath = new Path();
			seriesPath.getStyleClass().setAll("candlestick-average-line", "series" + seriesIndex);
			series.setNode(seriesPath);
			getPlotChildren().add(seriesPath);
			setLegend(createLegend());
		}

		@Override
		protected void seriesRemoved(Series<Number, String> series)
		{
			// remove all candle nodes
			for (XYChart.Data<Number, String> d : series.getData())
			{
				final Node candle = d.getNode();
				if (shouldAnimate())
				{
					// fade out old candle
					FadeTransition ft = new FadeTransition(Duration.millis(500), candle);
					ft.setToValue(0);
					ft.setOnFinished(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent actionEvent)
						{
							getPlotChildren().remove(candle);
						}
					});
					ft.play();
				}
				else
				{
					getPlotChildren().remove(candle);
				}
			}
		}

		/**
		 */
		private Node createBoxAndWhiskerNode(int seriesIndex, final Data item, int itemIndex)
		{
			Node boxAndWhiskerNode = item.getNode();
			// check if candle has already been created
			if (boxAndWhiskerNode instanceof BoxAndWhisker)
			{
				((BoxAndWhisker) boxAndWhiskerNode).setSeriesAndDataStyleClasses("series" + seriesIndex,
						"data" + itemIndex);
			}
			else
			{
				boxAndWhiskerNode = new BoxAndWhisker("series" + seriesIndex, "data" + itemIndex,
						seriesIndex);
				item.setNode(boxAndWhiskerNode);

				Series<Number, String> series = getData().get(seriesIndex);
				String key = series.getName() + series.getData().get(itemIndex).getYValue();
				NodeInformation nI = nodeInfoMap.get(key);

			}
			return boxAndWhiskerNode;
		}

		/**
		 * This is called when the range has been invalidated and we need to update it. If the axis are auto
		 * ranging then we compile a list of all data that the given axis has to plot and call
		 * invalidateRange() on the axis passing it that data.
		 */
		@Override
		protected void updateAxisRange()
		{
			// For candle stick chart we need to override this method as we need to let the axis know that
			// they need to be able
			// to cover the whole area occupied by the high to low range not just its center data value
			final Axis<Number> xa = getXAxis();
			final Axis<String> ya = getYAxis();
			List<Number> xData = null;
			List<String> yData = null;
			if (xa.isAutoRanging())
			{
				xData = new ArrayList<Number>();
			}
			if (ya.isAutoRanging())
			{
				yData = new ArrayList<String>();
			}
			if (xData != null || yData != null)
			{
				for (Series<Number, String> series : getData())
				{
					for (Data<Number, String> data : series.getData())
					{
						if (yData != null)
						{
							yData.add(data.getYValue());
						}
						if (xData != null)
						{
							BoxAndWhiskerExtraValues extras = (BoxAndWhiskerExtraValues) data.getExtraValue();
							if (extras.getMax() != null)
							{
								xData.add(extras.getMax());

							}

							if (extras.getMin() != null)
							{
								xData.add(extras.getMin());
							}

							xData.add(data.getXValue());

						}
					}
				}
				if (xData != null)
				{
					xa.invalidateRange(xData);
				}
				if (yData != null)
				{
					ya.invalidateRange(yData);
				}
			}
		}
	}

	/** Data extra values for storing close, high and low. */
	@SuppressWarnings("rawtypes")
	private class BoxAndWhiskerExtraValues extends ChartExtraValue
	{
		private Double	min;
		private Double	max;
		private Double	high;
		private String	description;

		public BoxAndWhiskerExtraValues(String label, Integer count, Double min, Double max, Double high,
				String description, Object userData)
		{
			super(label, count, userData);
			this.min = min;
			this.max = max;
			this.high = high;
			this.description = description;
		}

		public Double getMin()
		{
			return min;
		}

		public Double getMax()
		{
			return max;
		}

		public Double getHigh()
		{
			return high;
		}

	}

	/** Candle node used for drawing a candle */
	private class BoxAndWhisker extends Group
	{
		private Line	highLowLine		= new Line();
		private Line	topWhisker		= new Line();
		private Line	bottomWhisker	= new Line();
		private Region	bar				= new Region();
		private String	seriesStyleClass;
		private String	dataStyleClass;
		private boolean	openAboveClose	= true;
		private Tooltip	tooltip			= new Tooltip();
		private int		seriesIndex		= 0;

		private BoxAndWhisker(String seriesStyleClass, String dataStyleClass, int sI)
		{
			setAutoSizeChildren(false);

			topWhisker.resizeRelocate(0.0, 0.0, 0.0, 0.0);
			bottomWhisker.resizeRelocate(0.0, 0.0, 0.0, 0.0);
			getChildren().addAll(highLowLine, bar, topWhisker, bottomWhisker);
			this.seriesStyleClass = seriesStyleClass;
			this.dataStyleClass = dataStyleClass;
			this.seriesIndex = sI;
			updateStyleClasses();

		}

		public void setSeriesAndDataStyleClasses(String seriesStyleClass, String dataStyleClass)
		{
			this.seriesStyleClass = seriesStyleClass;
			this.dataStyleClass = dataStyleClass;

			updateStyleClasses();
		}

		public void update(Double closeOffset, Double highOffset, Double lowOffset, Double candleWidth,
				Double seriesOffset)
		{
			// openAboveClose = closeOffset > 0;
			updateStyleClasses();
			Double barX = 0.0;
			if (closeOffset == null)
			{
				closeOffset = candleWidth;
				barX = candleWidth / 2;
			}

			if (lowOffset == null)
				lowOffset = closeOffset;
			highLowLine.setStartX(lowOffset);

			if (highOffset == null || highOffset == 0)
			{
				highOffset = closeOffset;
				highLowLine.setEndX(highOffset - candleWidth);
			}
			else
			{
				highLowLine.setEndX(highOffset);
			}

			highLowLine.resizeRelocate(lowOffset, seriesOffset, highOffset - lowOffset, 2.0);
			if (candleWidth == -1)
			{
				candleWidth = bar.prefWidth(-1);
			}

			bar.resizeRelocate(-candleWidth / 2,
					-candleWidth / 2 + seriesOffset + highLowLine.getStrokeWidth() / 2, closeOffset,
					candleWidth);

			// bar.resizeRelocate(closeOffset,
			// -candleWidth / 2 + seriesOffset + highLowLine.getStrokeWidth() / 2, closeOffset * -1,
			// candleWidth);

			if (highOffset != closeOffset && highOffset > 0)
			{
				topWhisker.setStartY(seriesOffset);
				topWhisker.setEndY(candleWidth / 2 + seriesOffset);
				topWhisker.resizeRelocate(highOffset, -candleWidth / 4 + seriesOffset, candleWidth / 2,
						candleWidth / 2);
			}
			else
			{
				topWhisker.setVisible(false);
			}

			if (lowOffset != closeOffset)
			{
				bottomWhisker.setStartY(seriesOffset);
				bottomWhisker.setEndY(candleWidth / 2 + seriesOffset);
				bottomWhisker.resizeRelocate(lowOffset, -candleWidth / 4 + seriesOffset, candleWidth / 2,
						candleWidth / 2);
			}
			else
				bottomWhisker.setVisible(false);

		}

		private void updateStyleClasses()
		{
			int colorIndex = seriesIndex % 7;
			getStyleClass().setAll("boxwhisker-bar", seriesStyleClass, dataStyleClass);
			highLowLine.getStyleClass().setAll("boxwhisker-line", seriesStyleClass, dataStyleClass,
					"default-color" + colorIndex);
			bar.getStyleClass().setAll("boxwhisker-box", seriesStyleClass, dataStyleClass,
					"default-color" + colorIndex);
			topWhisker.getStyleClass().setAll("boxwhisker-line", seriesStyleClass, dataStyleClass,
					"default-color" + colorIndex);
			bottomWhisker.getStyleClass().setAll("boxwhisker-line", seriesStyleClass, dataStyleClass,
					"default-color" + colorIndex);

		}
	}

	/*
	 * generate box and whisker chart.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected Chart generateChart(String[] keys, ChartConfiguration chartConfig)
	{

		String minKey = keys[0];
		String maxKey = keys[1];
		String lowKey = keys[2];
		String key = keys[3];
		String middleKey = keys[4];
		Double axisMin = getMinMin(minKey);
		Double dataMin = axisMin;
		Double axisMax = getMaxMax(maxKey);
		if (axisMax == 0.0)
			axisMax = getMaxMax(key);

		if (axisMax < axisMin)
		{
			axisMax = 1.0;
			axisMin = 0.1;
		}

		CategoryAxis xAxis = new CategoryAxis();

		if (!lockXAxis.isSelected())
		{
			if (chartConfig != null && chartConfig.getMaxX() != null && chartConfig.getMinX() != null)
			{
				axisMax = chartConfig.getMaxX();
				axisMin = chartConfig.getMinX();
			}
		}
		final Axis yAxis;
		yAxis = SciomeNumberAxisGenerator.generateAxis(logXAxis.isSelected(), axisMin, axisMax, dataMin);

		xAxis.setLabel("Category");
		// yAxis.setLabel(minKey + "," + lowKey + "," + key + "," + maxKey);
		yAxis.setLabel(minKey + "," + key + "," + maxKey);
		RangePlot barChart = new RangePlot(xAxis, yAxis);

		barChart.setTitle("Range Plot");

		// Now put the data in a bucket

		// create count map because in multiple data comparison, I only care about
		// shared data labels
		Map<String, Integer> countMap = getCountMap();

		int maxPerPack = 0;
		if (chartDataPacks.size() > 0)
			maxPerPack = MAX_NODES / chartDataPacks.size();
		Double sum = 0.0;
		int count = 0;
		for (ChartDataPack chartDataPack : chartDataPacks)
		{
			SciomeSeries<Number, String> series1 = new SciomeSeries<>(chartDataPack.getName());

			Set<String> chartLabelSet = new HashSet<>();

			for (ChartData chartData : chartDataPack.getChartData())
			{
				if (cancel)
					return null;
				Double dataPointValue = (Double) chartData.getDataPoints().get(key);
				if (dataPointValue != null)
					sum += dataPointValue;

				count++;
			}
		}

		Double avg = 0.0;
		if (count > 0)
			avg = sum / count;

		for (ChartDataPack chartDataPack : chartDataPacks)
		{
			SciomeSeries<Number, String> series1 = new SciomeSeries<>(chartDataPack.getName());

			Set<String> chartLabelSet = new HashSet<>();

			for (ChartData chartData : chartDataPack.getChartData())
			{
				if (cancel)
					return null;
				Double dataPointValue = (Double) chartData.getDataPoints().get(key);

				if (dataPointValue == null)
					continue;

				sum += dataPointValue;

				Double dataPointValueMinKey = (Double) chartData.getDataPoints().get(minKey);
				Double dataPointValueLowKey = (Double) chartData.getDataPoints().get(lowKey);
				Double dataPointValueMaxKey = (Double) chartData.getDataPoints().get(maxKey);
				Double dataPointValueMiddleKey = (Double) chartData.getDataPoints().get(middleKey);

				chartLabelSet.add(chartData.getDataPointLabel());
				SciomeData<Number, String> xyData = new SciomeData<>(chartData.getDataPointLabel(),
						dataPointValue, chartData.getDataPointLabel(),
						new BoxAndWhiskerExtraValues(chartData.getDataPointLabel(),
								countMap.get(chartData.getDataPointLabel()), dataPointValueMinKey,
								dataPointValueMaxKey, dataPointValueMiddleKey,
								chartData.getCharttableObject().toString(), chartData.getCharttableObject()));

				series1.getData().add(xyData);

				nodeInfoMap.put(chartDataPack.getName() + chartData.getDataPointLabel(),
						new NodeInformation(chartData.getCharttableObject(), false));

				// too many nodes
				if (count > maxPerPack)
					break;

			}

			// add empty values for multiple datasets. When comparing multiple
			// data sets, it comes in handy for scrolling to just have empty
			// data points when the data set doesn't represent a label
			for (String chartedKey : countMap.keySet())
			{
				if (cancel)
					return null;
				if (!chartLabelSet.contains(chartedKey))
				{
					SciomeData<Number, String> xyData = new SciomeData<>(chartedKey, avg, chartedKey,
							new BoxAndWhiskerExtraValues(chartedKey, countMap.get(chartedKey), avg, avg, avg,
									"", null));

					series1.getData().add(xyData);
					nodeInfoMap.put(chartDataPack.getName() + chartedKey, new NodeInformation(null, true));
				}
			}

			if (seriesData.size() > 0)
				sortSeriesWithPrimarySeries(series1, (SciomeSeries) (seriesData.get(0)));
			else
				sortSeriesX(series1);
			seriesData.add(series1);

		}

		toolTip.setStyle("-fx-font: 14 arial;  -fx-font-smoothing-type: lcd;");

		return barChart;
	}

	private BoxAndWhisker userObjectPane(Object object, boolean invisible, int seriesIndex)
	{

		BoxAndWhisker boxAndWhiskerNode = new BoxAndWhisker("series" + seriesIndex, "data", seriesIndex);
		boxAndWhiskerNode.setUserData(object);

		if (invisible)
			boxAndWhiskerNode.setVisible(false);
		else
		{
			Tooltip.install(boxAndWhiskerNode, toolTip);

			boxAndWhiskerNode.setOnMouseEntered(new EventHandler<javafx.scene.input.MouseEvent>() {
				@Override
				public void handle(javafx.scene.input.MouseEvent arg0)
				{
					boxAndWhiskerNode.setEffect(new Glow());
					Object object = boxAndWhiskerNode.getUserData();
					if (object != null)
						toolTip.setText(String.valueOf(boxAndWhiskerNode.getUserData().toString()));

				}
			});

			// OnMouseExited
			boxAndWhiskerNode.setOnMouseExited(new EventHandler<javafx.scene.input.MouseEvent>() {
				@Override
				public void handle(javafx.scene.input.MouseEvent arg0)
				{
					boxAndWhiskerNode.setEffect(null);
				}
			});

			// OnMouseReleased
			boxAndWhiskerNode.setOnMouseReleased(new EventHandler<javafx.scene.input.MouseEvent>() {
				@Override
				public void handle(javafx.scene.input.MouseEvent mouseEvent)
				{
				}
			});
		}
		return boxAndWhiskerNode;
	}

	private class NodeInformation
	{

		public Object	object;
		public boolean	invisible;

		public NodeInformation(Object o, boolean i)
		{
			object = o;
			invisible = i;
		}
	}

	@Override
	protected Node getNode(String seriesName, String dataPointLabel, int seriesIndex)
	{
		NodeInformation nI = nodeInfoMap.get(seriesName + dataPointLabel);

		return userObjectPane(nI.object, nI.invisible, seriesIndex);
	}

	@Override
	protected boolean isXAxisDefineable()
	{
		return true;
	}

	@Override
	protected boolean isYAxisDefineable()
	{
		return false;
	}

	@Override
	protected void redrawChart()
	{
		initChart();

	}

	/*
	 * implement the getting of lines that need to be exported.
	 */
	@Override
	public List<String> getLinesToExport()
	{

		List<String> returnList = new ArrayList<>();
		StringBuilder sb = new StringBuilder();

		sb.append("series");
		sb.append("\t");
		sb.append("y");
		sb.append("\t");
		sb.append("min");
		sb.append("\t");
		sb.append("value");
		sb.append("\t");
		sb.append("max");
		sb.append("\t");
		sb.append("component");
		returnList.add(sb.toString());
		for (Object obj : this.seriesData)
		{
			SciomeSeries sData = (SciomeSeries) obj;
			for (Object d : sData.getData())
			{
				SciomeData xychartData = (SciomeData) d;
				BoxAndWhiskerExtraValues extraValue = (BoxAndWhiskerExtraValues) xychartData.getExtraValue();
				if (extraValue.description.equals("")) // this means it's a faked value for showing multiple
														// datasets together. skip it
					continue;
				sb.setLength(0);

				Double X = (Double) xychartData.getxValue();
				String Y = (String) xychartData.getyValue();

				sb.append(sData.getName());
				sb.append("\t");
				sb.append(Y);
				sb.append("\t");

				sb.append(extraValue.getMin());
				sb.append("\t");
				sb.append(X);
				sb.append("\t");
				sb.append(extraValue.getMax());
				sb.append("\t");
				sb.append(extraValue.description);

				returnList.add(sb.toString());

			}
		}

		return returnList;

	}

}
