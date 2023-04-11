package com.mendhak.gpslogger.loggers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Environment;
import android.util.Log;

import android.widget.EditText;

import com.mendhak.gpslogger.R;
import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.Session;
import com.mendhak.gpslogger.common.Utilities;


public class FileLoggingHelper
{

	private IFileLoggingHelperCallback callingClient;
	private FileLock gpxLock;
	private FileLock kmlLock;
	private static boolean allowDescription = false;

	public FileLoggingHelper(IFileLoggingHelperCallback callback)
	{
		callingClient = callback;
	}

	public void WriteToFile(Location loc)
	{


	
		if (!AppSettings.shouldLogToGpx() && !AppSettings.shouldLogToKml())
		{
			return;
		}

		try
		{

			boolean brandNewFile = false;

			File gpxFolder = new File(Environment.getExternalStorageDirectory(), "GPSLogger");

			if (!gpxFolder.exists())
			{
				gpxFolder.mkdirs();
				brandNewFile = true;
			}

			if (AppSettings.shouldLogToGpx())
			{
				WriteToGpxFile(loc, gpxFolder, brandNewFile);
			}

			if (AppSettings.shouldLogToKml())
			{
				WriteToKmlFile(loc, gpxFolder, brandNewFile);

			}

			allowDescription = true;

		}
		catch (Exception e)
		{
			Log.e("Main", "Could not write file " + e.getMessage());
			callingClient.SetStatus(callingClient.getString(R.string.could_not_write_to_file)
					+ e.getMessage());
		}

	}

	private void WriteToKmlFile(Location loc, File gpxFolder, boolean brandNewFile)
	{

		try
		{
			File kmlFile = new File(gpxFolder.getPath(), Session.getCurrentFileName() + ".kml");

			if (!kmlFile.exists())
			{
				kmlFile.createNewFile();
				brandNewFile = true;
			}

			Date now;

			if (AppSettings.shouldUseSatelliteTime())
			{
				now = new Date(loc.getTime());
			}
			else
			{
				now = new Date();
			}

			String dateTimeString = Utilities.GetIsoDateTime(now);
			// SimpleDateFormat sdf = new
			// SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			// String dateTimeString = sdf.format(now);

			if (brandNewFile)
			{
				FileOutputStream initialWriter = new FileOutputStream(kmlFile, true);
				BufferedOutputStream initialOutput = new BufferedOutputStream(initialWriter);

				String initialXml = "<?xml version=\"1.0\"?>"
						+ "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
						+"<Placemark><LineString><extrude>1</extrude><tessellate>1</tessellate><altitudeMode>absolute</altitudeMode><coordinates></coordinates></LineString></Placemark>"
						+ "</Document></kml>";
				initialOutput.write(initialXml.getBytes());
				// initialOutput.write("\n".getBytes());
				initialOutput.flush();
				initialOutput.close();
			}

			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true); 
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(kmlFile);
			
			NodeList coordinatesList = doc.getElementsByTagName("coordinates");
			
			if(coordinatesList.item(0) != null)
			{
				Node coordinates = coordinatesList.item(0);
				Node coordTextNode = coordinates.getFirstChild();
				
				if(coordTextNode == null)
				{
					coordTextNode = doc.createTextNode("");
					coordinates.appendChild(coordTextNode);
				}
				
				String coordText = coordinates.getFirstChild().getNodeValue();
				coordText = coordText + "\n" + String.valueOf(loc.getLongitude()) + ","
					+ String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getAltitude());
				coordinates.getFirstChild().setNodeValue(coordText);
				
			}

			Node documentNode = doc.getElementsByTagName("Document").item(0);
			Node newPlacemark = doc.createElement("Placemark");
			
			Node timeStamp = doc.createElement("TimeStamp");
			Node whenNode = doc.createElement("when");
			Node whenNodeText = doc.createTextNode(dateTimeString);
			whenNode.appendChild(whenNodeText);
			timeStamp.appendChild(whenNode);
			newPlacemark.appendChild(timeStamp);
			
			Node newPoint = doc.createElement("Point");
			
			Node newCoords = doc.createElement("coordinates");
			Node newCoordTextNode = doc.createTextNode("");
			newCoords.appendChild(newCoordTextNode);
			
			newCoords.getFirstChild().setNodeValue( String.valueOf(loc.getLongitude()) + ","
					+ String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getAltitude()));
			newPoint.appendChild(newCoords);
			
			newPlacemark.appendChild(newPoint);
			
			documentNode.appendChild(newPlacemark);

			String newFileContents = getStringFromNode(doc);
			
			RandomAccessFile raf = new RandomAccessFile(kmlFile, "rw");
			kmlLock = raf.getChannel().lock();
			raf.write(newFileContents.getBytes());
			kmlLock.release();
			raf.close();
		
		}
		catch (IOException e)
		{
			Log.e("Main", callingClient.getString(R.string.could_not_write_to_file) + e.getMessage());
			callingClient.SetStatus(callingClient.getString(R.string.could_not_write_to_file)
					+ e.getMessage());
		}
		catch(Exception e)
		{
			System.out.println(e.getMessage());
			Log.e("Main", callingClient.getString(R.string.could_not_write_to_file) + e.getMessage());
			callingClient.SetStatus(callingClient.getString(R.string.could_not_write_to_file)
					+ e.getMessage());
		}

	}
	
	
	private static String getStringFromNode(Node root)  {

        StringBuilder result = new StringBuilder();

        if (root.getNodeType() == Node.TEXT_NODE)
        {
            result.append(root.getNodeValue());
        }
        else 
        {
            if (root.getNodeType() != Node.DOCUMENT_NODE) 
            {
                StringBuffer attrs = new StringBuffer();
                for (int k = 0; k < root.getAttributes().getLength(); ++k) 
                {
                    attrs.append(" ") 
                    	.append(root.getAttributes().item(k).getNodeName())
                    	.append("=\"")
                    	.append(root.getAttributes().item(k).getNodeValue())
                    	.append("\" ");
                }
                result.append("<")
                	.append(root.getNodeName());
                
                if(attrs.length() > 0)
                {
                	result.append(" ")
                	.append(attrs);
                }
                	
                	result.append(">");
            } 
            else 
            {
                result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            }

            NodeList nodes = root.getChildNodes();
            for (int i = 0, j = nodes.getLength(); i < j; i++) 
            {
                Node node = nodes.item(i);
                result.append(getStringFromNode(node));
            }

            if (root.getNodeType() != Node.DOCUMENT_NODE)
            {
                result.append("</").append(root.getNodeName()).append(">");
            }
        }
        return result.toString();
    }
	

	
	private void WriteToGpxFile(Location loc, File gpxFolder, boolean brandNewFile)
	{

		try
		{
			File gpxFile = new File(gpxFolder.getPath(), Session.getCurrentFileName() + ".gpx");

			if (!gpxFile.exists())
			{
				gpxFile.createNewFile();
				brandNewFile = true;
			}

			Date now;

			if (AppSettings.shouldUseSatelliteTime())
			{
				now = new Date(loc.getTime());
			}
			else
			{
				now = new Date();
			}

			String dateTimeString = Utilities.GetIsoDateTime(now);

			if (brandNewFile)
			{
				FileOutputStream initialWriter = new FileOutputStream(gpxFile, true);
				BufferedOutputStream initialOutput = new BufferedOutputStream(initialWriter);

				String initialXml = "<?xml version=\"1.0\"?>"
						+ "<gpx version=\"1.0\" creator=\"GPSLogger - http://gpslogger.mendhak.com/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.topografix.com/GPX/1/0\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">"
						+ "<time>" + dateTimeString + "</time>" + "<bounds />" + "<trk></trk></gpx>";
				initialOutput.write(initialXml.getBytes());
				// initialOutput.write("\n".getBytes());
				initialOutput.flush();
				initialOutput.close();
			}

			int offsetFromEnd = (Session.shouldAddNewTrackSegment()) ? 12 : 21;

			long startPosition = gpxFile.length() - offsetFromEnd;

			String trackPoint = GetTrackPointXml(loc, dateTimeString);

			Session.setAddNewTrackSegment(false);

			// Leaving this commented code in - may want to give user the choice
			// to
			// pick between WPT and TRK. Choice is good.
			//
			// String waypoint = "<wpt lat=\"" +
			// String.valueOf(loc.getLatitude())
			// + "\" lon=\"" + String.valueOf(loc.getLongitude()) + "\">"
			// + "<time>" + dateTimeString + "</time>";
			//
			// if (loc.hasAltitude()) {
			// waypoint = waypoint + "<ele>"
			// + String.valueOf(loc.getAltitude()) + "</ele>";
			// }
			//
			// if (loc.hasBearing()) {
			// waypoint = waypoint + "<course>"
			// + String.valueOf(loc.getBearing()) + "</course>";
			// }
			//
			// if (loc.hasSpeed()) {
			// waypoint = waypoint + "<speed>"
			// + String.valueOf(loc.getSpeed()) + "</speed>";
			// }
			//
			// waypoint = waypoint + "<src>" + loc.getProvider() + "</src>";
			//
			// if (satellites > 0) {
			// waypoint = waypoint + "<sat>" + String.valueOf(satellites)
			// + "</sat>";
			// }
			//
			// waypoint = waypoint + "</wpt></gpx>";

			RandomAccessFile raf = new RandomAccessFile(gpxFile, "rw");
			gpxLock = raf.getChannel().lock();
			raf.seek(startPosition);
			raf.write(trackPoint.getBytes());
			gpxLock.release();
			raf.close();

		}
		catch (IOException e)
		{
			Log.e("Main", callingClient.getString(R.string.could_not_write_to_file) + e.getMessage());
			callingClient.SetStatus(callingClient.getString(R.string.could_not_write_to_file)
					+ e.getMessage());
		}

	}

	private String GetTrackPointXml(Location loc, String dateTimeString)
	{
		String track = "";
		if (Session.shouldAddNewTrackSegment())
		{
			track = track + "<trkseg>";
		}

		track = track + "<trkpt lat=\"" + String.valueOf(loc.getLatitude()) + "\" lon=\""
				+ String.valueOf(loc.getLongitude()) + "\">";

		if (loc.hasAltitude())
		{
			track = track + "<ele>" + String.valueOf(loc.getAltitude()) + "</ele>";
		}

		if (loc.hasBearing())
		{
			track = track + "<course>" + String.valueOf(loc.getBearing()) + "</course>";
		}

		if (loc.hasSpeed())
		{
			track = track + "<speed>" + String.valueOf(loc.getSpeed()) + "</speed>";
		}

		track = track + "<src>" + loc.getProvider() + "</src>";

		if(Session.getSatelliteCount()>0)
		{
			track = track + "<sat>" + String.valueOf(Session.getSatelliteCount()) + "</sat>";
		}

		track = track + "<time>" + dateTimeString + "</time>";

		track = track + "</trkpt>";

		track = track + "</trkseg></trk></gpx>";

		return track;
	}

	public void Annotate()
	{

		if (!allowDescription)
		{
			Utilities.MsgBox(callingClient.GetContext().getString(R.string.not_yet),
					callingClient.getString(R.string.cant_add_description_until_next_point),
					callingClient.GetActivity());

			return;

		}

		AlertDialog.Builder alert = new AlertDialog.Builder(callingClient.GetActivity());

		alert.setTitle(R.string.add_description);
		alert.setMessage(R.string.letters_numbers);

		// Set an EditText view to get user input
		final EditText input = new EditText(callingClient.GetContext());
		alert.setView(input);

		alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{

				if (!AppSettings.shouldLogToGpx() && !AppSettings.shouldLogToKml())
				{
					return;
				}

				final String desc = Utilities.CleanDescription(input.getText().toString());

				AddNoteToLastPoint(desc);

			}

		});
		alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				// Canceled.
			}
		});

		alert.show();

	}

	private void AddNoteToLastPoint(String desc)
	{

		File gpxFolder = new File(Environment.getExternalStorageDirectory(), "GPSLogger");

		if (!gpxFolder.exists())
		{
			return;
		}

		int offsetFromEnd;
		String description;
		long startPosition;

		if (AppSettings.shouldLogToGpx())
		{

			File gpxFile = new File(gpxFolder.getPath(), Session.getCurrentFileName() + ".gpx");
			//File gpxFile = new File(gpxFolder.getPath(), callingClient.GetCurrentFileName() + ".gpx");

			if (!gpxFile.exists())
			{
				return;
			}
			offsetFromEnd = 29;

			startPosition = gpxFile.length() - offsetFromEnd;

			description = "<name>" + desc + "</name><desc>" + desc
					+ "</desc></trkpt></trkseg></trk></gpx>";
			RandomAccessFile raf = null;
			try
			{
				raf = new RandomAccessFile(gpxFile, "rw");
				gpxLock = raf.getChannel().lock();
				raf.seek(startPosition);
				raf.write(description.getBytes());
				gpxLock.release();
				raf.close();

				callingClient.SetStatus(callingClient.getString(R.string.description_added));
				allowDescription = false;

			}
			catch (Exception e)
			{
				callingClient.SetStatus(callingClient.getString(R.string.could_not_write_to_file));
			}

		}

		if (AppSettings.shouldLogToKml())
		{

			File kmlFile = new File(gpxFolder.getPath(), Session.getCurrentFileName() + ".kml");

			if (!kmlFile.exists())
			{
				return;
			}

			offsetFromEnd = 37;

			description = "<name>" + desc + "</name></Point></Placemark></Document></kml>";

			startPosition = kmlFile.length() - offsetFromEnd;
			try
			{
				RandomAccessFile raf = new RandomAccessFile(kmlFile, "rw");
				kmlLock = raf.getChannel().lock();
				raf.seek(startPosition);
				raf.write(description.getBytes());
				kmlLock.release();
				raf.close();

				allowDescription = false;
			}
			catch (Exception e)
			{
				callingClient.SetStatus(callingClient.getString(R.string.could_not_write_to_file));
			}

		}

		// </Point></Placemark></Document></kml>

	}

}