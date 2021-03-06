package com.graphhopper.api;

import com.graphhopper.PathWrapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.exceptions.PointOutOfBoundsException;
import com.graphhopper.util.shapes.GHPoint;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.Before;

import java.net.SocketTimeoutException;

/**
 * @author Peter Karich
 */
public class GraphHopperWebIT {

    public static final String KEY = "614b8305-b4db-48c9-bf4a-40de90919939";

    private final GraphHopperWeb gh = new GraphHopperWeb();
    private final GraphHopperMatrixWeb ghMatrix = new GraphHopperMatrixWeb();

    @Before
    public void setUp() {
        String key = System.getProperty("graphhopper.key", KEY);
        gh.setKey(key);
        ghMatrix.setKey(key);
    }

    @Test
    public void testSimpleRoute() {
        // https://graphhopper.com/maps/?point=49.6724%2C11.3494&point=49.655%2C11.418
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        PathWrapper alt = res.getBest();
        isBetween(200, 250, alt.getPoints().size());
        isBetween(9900, 10300, alt.getDistance());

        // change vehicle
        res = gh.route(new GHRequest(49.6724, 11.3494, 49.6550, 11.4180).
                setVehicle("bike"));
        alt = res.getBest();
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        isBetween(9000, 9500, alt.getDistance());
    }

    @Test
    public void testTimeout() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());

        req.getHints().put(GraphHopperWeb.TIMEOUT, 1);
        try {
            res = gh.route(req);
            fail();
        } catch (RuntimeException e) {
            assertEquals(SocketTimeoutException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testNoPoints() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));

        req.getHints().put("instructions", false);
        req.getHints().put("calc_points", false);
        GHResponse res = gh.route(req);
        assertFalse("errors:" + res.getErrors().toString(), res.hasErrors());
        PathWrapper alt = res.getBest();
        assertEquals(0, alt.getPoints().size());
        isBetween(9900, 10300, alt.getDistance());
    }

    @Test
    public void readRoundabout() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));

        GHResponse res = gh.route(req);
        int counter = 0;
        for (Instruction i : res.getBest().getInstructions()) {
            if (i instanceof RoundaboutInstruction) {
                counter++;
                RoundaboutInstruction ri = (RoundaboutInstruction) i;
                assertEquals("turn_angle was incorrect:" + ri.getTurnAngle(), -1.5, ri.getTurnAngle(), 0.1);
                // This route contains only one roundabout and no (via) point in a roundabout
                assertEquals("exited was incorrect:" + ri.isExited(), ri.isExited(), true);
            }
        }
        assertTrue("no roundabout in route?", counter > 0);
    }

    @Test
    public void testRetrieveOnlyStreetname() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));

        GHResponse res = gh.route(req);
        assertEquals("Turn right onto B 246", res.getBest().getInstructions().get(4).getName());

        req.getHints().put("turn_description", false);
        res = gh.route(req);
        assertEquals("B 246", res.getBest().getInstructions().get(4).getName());
    }

    @Test
    public void testCannotFindPointException() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(-4.214943, -130.078125)).
                addPoint(new GHPoint(39.909736, -91.054687));

        GHResponse res = gh.route(req);
        assertTrue("no erros found?", res.hasErrors());
        assertTrue(res.getErrors().get(0) instanceof PointNotFoundException);
    }


    @Test
    public void testOutOfBoundsException() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(-400.214943, -130.078125)).
                addPoint(new GHPoint(39.909736, -91.054687));

        GHResponse res = gh.route(req);
        assertTrue("no erros found?", res.hasErrors());
        assertTrue(res.getErrors().get(0) instanceof PointOutOfBoundsException);
    }

    @Test
    public void readFinishInstruction() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(52.261434, 13.485718)).
                addPoint(new GHPoint(52.399067, 13.469238));

        GHResponse res = gh.route(req);
        InstructionList instructions = res.getBest().getInstructions();
        String finishInstructionName = instructions.get(instructions.size() - 1).getName();
        assertEquals("Finish!", finishInstructionName);
    }

    @Test
    public void testSimpleExport() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        req.getHints().put("type", "gpx");
        String res = gh.export(req);
        assertTrue(res.contains("<gpx"));
        assertTrue(res.contains("<rtept lat="));
        assertTrue(res.contains("<trk><name>GraphHopper Track</name><trkseg>"));
        assertTrue(res.endsWith("</gpx>"));
    }

    @Test
    public void testExportWithoutTrack() {
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        req.getHints().put("elevation", false);
        req.getHints().put("instructions", true);
        req.getHints().put("calc_points", true);
        req.getHints().put("type", "gpx");
        req.getHints().put("gpx.track", "false");
        String res = gh.export(req);
        assertTrue(res.contains("<gpx"));
        assertTrue(res.contains("<rtept lat="));
        assertTrue(!res.contains("<trk><name>GraphHopper Track</name><trkseg>"));
        assertTrue(res.endsWith("</gpx>"));
    }

    void isBetween(double from, double to, double expected) {
        assertTrue("expected value " + expected + " was smaller than limit " + from, expected >= from);
        assertTrue("expected value " + expected + " was bigger than limit " + to, expected <= to);
    }

    @Test
    public void testMatrix() {
        GHMRequest req = AbstractGHMatrixWebTester.createRequest();
        MatrixResponse res = ghMatrix.route(req);

        // no distances available
        try {
            assertEquals(0, res.getDistance(1, 2), 1);
            assertTrue(false);
        } catch (Exception ex) {
        }

        // ... only weight:
        assertEquals(1680, res.getWeight(1, 2), 5);

        req = AbstractGHMatrixWebTester.createRequest();
        req.addOutArray("weights");
        req.addOutArray("distances");
        res = ghMatrix.route(req);

        assertEquals(9637, res.getDistance(1, 2), 20);
        assertEquals(1680, res.getWeight(1, 2), 10);
    }

    @Test
    public void testUnknownInstructionSign() {
        // Actual path for the request: point=48.354413%2C8.676335&point=48.35442%2C8.676345
        // Modified the sign though
        JSONObject json = new JSONObject("{\"instructions\":[{\"distance\":1.073,\"sign\":741,\"interval\":[0,1],\"text\":\"Continue onto A 81\",\"time\":32,\"street_name\":\"A 81\"},{\"distance\":0,\"sign\":4,\"interval\":[1,1],\"text\":\"Finish!\",\"time\":0,\"street_name\":\"\"}],\"descend\":0,\"ascend\":0,\"distance\":1.073,\"bbox\":[8.676286,48.354446,8.676297,48.354453],\"weight\":0.032179,\"time\":32,\"points_encoded\":true,\"points\":\"gfcfHwq}s@}c~AAA?\",\"snapped_waypoints\":\"gfcfHwq}s@}c~AAA?\"}");
        PathWrapper wrapper = GraphHopperWeb.createAltResponse(json, true, true, true, true);

        assertEquals(741, wrapper.getInstructions().get(0).getSign());
        assertEquals("Continue onto A 81", wrapper.getInstructions().get(0).getName());
    }
}
