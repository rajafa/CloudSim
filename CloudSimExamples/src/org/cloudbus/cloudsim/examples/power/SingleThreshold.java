package org.cloudbus.cloudsim.examples.power;

/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation
 *               of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009, The University of Melbourne, Australia
 */

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerDynamicWorkloadFixedTime;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelStochastic;
import org.cloudbus.cloudsim.UtilizationModelWorkHour;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerPe;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicySTLeastMigCost;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicySimAnneal;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicySingleThreshold;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.examples.UtilizationModelUniform;
import org.cloudbus.cloudsim.examples.UtilizationModelZipfDistr;

/**
 * An example of a power aware data center. In this example the placement of VMs
 * is continuously adapted using VM migration in order to minimize the number
 * of physical nodes in use, while idle nodes are switched off to save energy.
 * The CPU utilization of each host is kept under the specified utilization threshold.
 */
public class SingleThreshold {

	protected static final int simLength = 120*30; //one hour

	/** The cloudlet list. */
	protected static List<Cloudlet> cloudletList;

	/** The vm list. */
	protected static List<Vm> vmList;

	protected static double utilizationThreshold = 0.7;

	protected static double hostsNumber = 6;//10;
	protected static double vmsNumber = 12;//20;
	protected static double cloudletsNumber = vmsNumber;//20;
	protected static boolean workHourLoad = true;
	protected static boolean useSA = true;
	protected static int roughIndex = 3;
	protected static boolean useAverageUtilization = true;

	//protected static UtilizationModelStochastic utilizationModelWorkHour;
	protected static UtilizationModelUniform utilizationModelUniform;
	protected static UtilizationModelStochastic utilizationModelStochastic;

	/**
	 * Creates main() to run this example.
	 *
	 * @param args the args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {

		Log.setOutputFile("C:\\Users\\n7682905\\sim.txt");
		Log.printLine("Starting SingleThreshold example...");

		try {
			// First step: Initialize the CloudSim package. It should be called
			// before creating any entities. We can't run this example without
			// initializing CloudSim first. We will get run-time exception
			// error.
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace GridSim events

			// Initialize the CloudSim library
			CloudSim.init(num_user, calendar, trace_flag);

			// Second step: Create Datacenters
			// Datacenters are the resource providers in CloudSim. We need at
			// list one of them to run a CloudSim simulation
			PowerDatacenter datacenter = createDatacenter("Datacenter_0");

			// Third step: Create Broker
			DatacenterBroker broker = createBroker();
			int brokerId = broker.getId();

			// Fourth step: Create one virtual machine
			vmList = createVms(brokerId);

			// submit vm list to the broker
			broker.submitVmList(vmList);

			// Fifth step: Create one cloudlet

			cloudletList = createCloudletList(brokerId);

			// submit cloudlet list to the broker
			broker.submitCloudletList(cloudletList);

			// Sixth step: Starts the simulation
			double lastClock = CloudSim.startSimulation();

			// Final step: Print results when simulation is over
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			Log.printLine("Received " + newList.size() + " cloudlets");

			CloudSim.stopSimulation();

			printCloudletList(newList);

		    int totalTotalRequested = 0;
		    int totalTotalAllocated = 0;
		    ArrayList<Double> sla = new ArrayList<Double>();
		    int numberOfAllocations = 0;
			for (Entry<String, List<List<Double>>> entry : datacenter.getUnderAllocatedMips().entrySet()) {
			    List<List<Double>> underAllocatedMips = entry.getValue();
			    double totalRequested = 0;
			    double totalAllocated = 0;
			    for (List<Double> mips : underAllocatedMips) {
			    	if (mips.get(0) != 0) {
			    		numberOfAllocations++;
			    		totalRequested += mips.get(0);
			    		totalAllocated += mips.get(1);
			    		double _sla = (mips.get(0) - mips.get(1)) / mips.get(0) * 100;
			    		if (_sla > 0) {
			    			sla.add(_sla);
			    		}
			    	}
				}
			    totalTotalRequested += totalRequested;
			    totalTotalAllocated += totalAllocated;
			}

			double averageSla = 0;
			if (sla.size() > 0) {
			    double totalSla = 0;
			    for (Double _sla : sla) {
			    	totalSla += _sla;
				}
			    averageSla = totalSla / sla.size();
			}

			Log.printLine();
			Log.printLine(String.format("Total simulation time: %.2f sec", lastClock));
			Log.printLine(String.format("Energy consumption: %.4f kWh", datacenter.getPower() / (3600 * 1000)));
			Log.printLine(String.format("Number of VM migrations: %d", datacenter.getMigrationCount()));
			Log.printLine(String.format("Number of SLA violations: %d", sla.size()));
			Log.printLine(String.format("SLA violation percentage: %.2f%%", (double) sla.size() * 100 / numberOfAllocations));
			Log.printLine(String.format("Average SLA violation: %.2f%%", averageSla));
			Log.printLine();
			
			Log.printLineToInfoFile(datacenter.getVmAllocationPolicy().getPolicyDesc(),simLength, 
					datacenter.getMigrationCount(),
					(double) sla.size() * 100 / numberOfAllocations,
					averageSla,
					datacenter.getPower() / (3600 * 1000));
			utilizationModelStochastic.saveHistory("c:\\users\\n7682905\\simWorkload.txt");

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}

		Log.printLine("SingleThreshold finished!");
		System.out.println("SingleThreshold finished!");
	}

	/**
	 * Creates the cloudlet list.
	 *
	 * @param brokerId the broker id
	 *
	 * @return the cloudlet list
	 */
	protected static List<Cloudlet> createCloudletList(int brokerId) {
		
		if (workHourLoad)
			utilizationModelStochastic = new UtilizationModelWorkHour(roughIndex);
		else
			utilizationModelStochastic = new UtilizationModelStochastic(roughIndex);
		utilizationModelUniform = new UtilizationModelUniform() ;
		
		List<Cloudlet> list = new ArrayList<Cloudlet>();

		long length = 150000; // 10 min on 250 MIPS
		int pesNumber = 1;
		long fileSize = 300;
		long outputSize = 300;

		for (int i = 0; i < cloudletsNumber; i++) {
			Cloudlet cloudlet = null;
			if (i==0){
				cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModelStochastic, new UtilizationModelStochastic(roughIndex), new UtilizationModelStochastic(roughIndex));
			}else{
				if (workHourLoad)
					cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, new UtilizationModelWorkHour(roughIndex), new UtilizationModelWorkHour(roughIndex-2), new UtilizationModelStochastic(roughIndex));
				else
					cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, new UtilizationModelStochastic(roughIndex), new UtilizationModelStochastic(roughIndex), new UtilizationModelStochastic(roughIndex));
			}
			cloudlet.setUserId(brokerId);
			cloudlet.setVmId(i);
			cloudlet.setCloudletDuration(simLength); // 20 minutes
			list.add(cloudlet);
		}

		return list;
	}

	/**
	 * Creates the vms.
	 *
	 * @param brokerId the broker id
	 *
	 * @return the list< vm>
	 */
	protected static List<Vm> createVms(int brokerId) {
		List<Vm> vms = new ArrayList<Vm>();

		// VM description
		int[] mips = { 250, 500, 750, 1000 }; // MIPSRating
		int pesNumber = 1; // number of cpus
		int[] rams =    { 250, 500, 750, 1000 };//{128, 256, 374, 512 }; // vm memory (MB)
		long bw = 2500; // bandwidth
		long size = 2500; // image size (MB)
		String vmm = "Xen"; // VMM name

		for (int i = 0; i < vmsNumber; i++) {
			vms.add(
				new Vm(i, brokerId, mips[i % mips.length], pesNumber, rams[i % mips.length], bw, size, vmm, new CloudletSchedulerDynamicWorkloadFixedTime(mips[i % mips.length], pesNumber,rams[i % mips.length]))
			);
		}

		return vms;
	}

	/**
	 * Creates the datacenter.
	 *
	 * @param name the name
	 *
	 * @return the datacenter
	 *
	 * @throws Exception the exception
	 */
	protected static PowerDatacenter createDatacenter(String name) throws Exception {
		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create an object of HostList2 to store
		// our machine
		List<PowerHost> hostList = new ArrayList<PowerHost>();

		double maxPower = 250; // 250W
		double staticPowerPercent = 0.7; // 70%

		int[] mips = { 1000, 2000, 3000 };
		//int[] mips = { 2000, 2000, 2000 };
		int ram = 10000; // host memory (MB)
		long storage = 1000000; // host storage
		int bw = 100000;

		for (int i = 0; i < hostsNumber; i++) {
			// 2. A Machine contains one or more PEs or CPUs/Cores.
			// In this example, it will have only one core.
			// 3. Create PEs and add these into an object of PowerPeList.
			List<PowerPe> peList = new ArrayList<PowerPe>();
			peList.add(new PowerPe(0, new PeProvisionerSimple(mips[i % mips.length]), new PowerModelLinear(maxPower+ 100 *  (i % mips.length), staticPowerPercent))); // need to store PowerPe id and MIPS Rating

			// 4. Create PowerHost with its id and list of PEs and add them to the list of machines
			hostList.add(
				new PowerHost(
					i,
					new RamProvisionerSimple(mips[i % mips.length]), //ram
					new BwProvisionerSimple(bw),
					storage,
					peList,
					new VmSchedulerTimeShared(peList)
				)
			); // This is our machine
		}

		// 5. Create a DatacenterCharacteristics object that stores the
		// properties of a Grid resource: architecture, OS, list of
		// Machines, allocation policy: time- or space-shared, time zone
		// and its price (G$/PowerPe time unit).
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

		// 6. Finally, we need to create a PowerDatacenter object.
		PowerDatacenter powerDatacenter = null;
		try {
			VmAllocationPolicy vmAllocationPolicy = null;
			if (useSA)
				vmAllocationPolicy = new PowerVmAllocationPolicySimAnneal(hostList, utilizationThreshold);
			else
				vmAllocationPolicy = new PowerVmAllocationPolicySingleThreshold(hostList, utilizationThreshold);
			
			powerDatacenter = new PowerDatacenter(
					name,
					characteristics,
					vmAllocationPolicy,
					new LinkedList<Storage>(),
					5.0);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return powerDatacenter;
	}

	// We strongly encourage users to develop their own broker policies, to
	// submit vms and cloudlets according
	// to the specific rules of the simulated scenario
	/**
	 * Creates the broker.
	 *
	 * @return the datacenter broker
	 */
	protected static DatacenterBroker createBroker() {
		DatacenterBroker broker = null;
		try {
			broker = new DatacenterBroker("Broker");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return broker;
	}

	/**
	 * Prints the Cloudlet objects.
	 *
	 * @param list list of Cloudlets
	 */
	protected static void printCloudletList(List<Cloudlet> list) {
		int size = list.size();
		Cloudlet cloudlet;

		String indent = "\t";
		Log.printLine();
		Log.printLine("========== OUTPUT ==========");
		Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
				+ "Resource ID" + indent + "VM ID" + indent + "Time" + indent
				+ "Start Time" + indent + "Finish Time");

		DecimalFormat dft = new DecimalFormat("###.##");
		for (int i = 0; i < size; i++) {
			cloudlet = list.get(i);
			Log.print(indent + cloudlet.getCloudletId());

			if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
				Log.printLine(indent + "SUCCESS"
					+ indent + indent + cloudlet.getResourceId()
					+ indent + cloudlet.getVmId()
					+ indent + dft.format(cloudlet.getActualCPUTime())
					+ indent + dft.format(cloudlet.getExecStartTime())
					+ indent + indent + dft.format(cloudlet.getFinishTime())
				);
			}
		}
	}

}
