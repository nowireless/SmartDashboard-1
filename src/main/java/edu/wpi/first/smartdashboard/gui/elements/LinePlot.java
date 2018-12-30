package edu.wpi.first.smartdashboard.gui.elements;

import edu.wpi.first.smartdashboard.gui.DashboardFrame;
import edu.wpi.first.smartdashboard.gui.Widget;
import edu.wpi.first.smartdashboard.gui.elements.bindings.AbstractValueWidget;
import edu.wpi.first.smartdashboard.properties.BooleanProperty;
import edu.wpi.first.smartdashboard.properties.IntegerProperty;
import edu.wpi.first.smartdashboard.properties.Property;
import edu.wpi.first.smartdashboard.robot.Robot;
import edu.wpi.first.smartdashboard.types.DataType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * @author pmalmsten
 */
public class LinePlot extends AbstractValueWidget {

  public static final int kDefaultPollingPeriodMS = 100;
  public static final DataType[] TYPES = {DataType.NUMBER};
  public final IntegerProperty bufferSize
      = new IntegerProperty(this, "Buffer Size (samples)", 5000);
  public final BooleanProperty clear = new BooleanProperty(this, "Clear Graph", false);
  public final BooleanProperty pollEnable = new BooleanProperty(this, "Enable Polling", false);
  public final IntegerProperty pollPeriod = new IntegerProperty(this, "Poll Rate (ms)", kDefaultPollingPeriodMS);

  private String m_key;
  private Object m_lock = new Object();
  private boolean m_polling = false;
  private int m_pollingPeriod = kDefaultPollingPeriodMS;
  private Thread m_pollingThread = new Thread(new Runnable() {
    @Override
    public void run() {
      while(true) {
        double period;
        synchronized (m_lock) {
          // Get out if not polling
          if(!m_polling) continue;
          period = m_pollingPeriod;

          System.out.println("Polling");

          if(Robot.getTable().containsKey(m_key)) {
            double value = Robot.getTable().getNumber(m_key, 0);
            updateDataSet(value);
          }
        }

        try {
          Thread.sleep((long) period);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  });

  ChartPanel m_chartPanel;
  XYSeries m_data;
  XYDataset m_dataset;
  JFreeChart m_chart;
  double startTime;

  @Override
  public void init() {
    setLayout(new BorderLayout());

    m_data = new XYSeries(getFieldName());
    m_dataset = new XYSeriesCollection(m_data);

    startTime = System.currentTimeMillis() / 1000.0;

    JFreeChart chart = ChartFactory.createXYLineChart(
        getFieldName(),
        "Time (Seconds)",
        "Data",
        m_dataset,
        PlotOrientation.VERTICAL,
        false,
        true,
        false);

    m_chartPanel = new ChartPanel(chart);
    m_chartPanel.setPreferredSize(new Dimension(400, 300));
    m_chartPanel.setBackground(getBackground());

    JMenuItem clearMenuItem = new JMenuItem("Clear");
    clearMenuItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if ("Clear" == e.getActionCommand()) {
          m_data.clear();
        }
      }
    });
    m_chartPanel.getPopupMenu().add(clearMenuItem);

    add(m_chartPanel, BorderLayout.CENTER);
    revalidate();
    repaint();

    // Search for the key associated with this property
    Map<String, Widget> fields = DashboardFrame.getInstance().getSmartdashboardFields();
    for (Map.Entry<String, Widget> entry : fields.entrySet()) {
      if (entry.getValue() == this) {
        m_key = entry.getKey();
      }
    }
    m_pollingThread.start();
  }

  @Override
  public void setValue(double value) { //TODO make sample in thread instead of relying on set
    // Ignore updates if polling
    if(m_polling) return;

    synchronized (m_lock) {
      updateDataSet(value);
    }
  }

  private void updateDataSet(double value) {
    // value (so that the widget has even time scale)
    m_data.add(System.currentTimeMillis() / 1000.0 - startTime, value);

    if (m_data.getItemCount() > bufferSize.getValue()) {
      m_data.remove(0);
    }

    revalidate();
    repaint();
  }

  @Override
  public void propertyChanged(Property property) {
    synchronized (m_lock) {
      if (property == bufferSize) {

        while (m_data.getItemCount() > bufferSize.getValue()) {
          m_data.remove(0);
        }
      }
      if (property == clear) {
        if (clear.getValue()) {
          m_data.clear();
          clear.setValue(false);
          startTime = System.currentTimeMillis() / 1000.0;
        }
      }

      if (property == pollEnable) {
        m_polling = (boolean) property.getValue();
      }

      if (property == pollPeriod) {
        m_pollingPeriod = (int) property.getValue();
      }
    }
  }
}
