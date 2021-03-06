/*
 Based on Barhk JAVA code for LD001.
 Adapted for Nova3D by X3msnake
 RecodeVer 2.191208
*/

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class nova3DPlugin {
  public static void main(String... args) {

    try {
      System.out.println("Out to:" + args[0] + "slice.gcode\n");

      OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(args[0] + "slice.gcode"));
      StringBuilder template = new StringBuilder();

      template.append(";v1.0 - all variables are accessible by Apache Freemaker SQUARE_BRACKET_INTERPOLATION_SYNTAX\n");
      template.append(";you can review final template with ;printFreemakerTemplate=true comment\n");

      BufferedReader isr = null;

      try {
        isr = new BufferedReader(new FileReader(args[0] + "run.gcode"));
        Map<String, Object> data = new HashMap<>();
        boolean header = true;
        boolean startCode = false;
        boolean printCode = false;
        boolean endCode = false;
        boolean printData = false;
        boolean freemakerTemplate = false;
        double bottomLayers = 0;
        List<Map<String, Object>> slices = new ArrayList<>();
        data.put("slices", slices);
        HashMap<String, Object> slice;
        for (String line = isr.readLine(); line != null; line = isr.readLine()) {
          if (line.startsWith(";printFreemakerTemplate=true"))
            freemakerTemplate = true;
          else if (line.startsWith(";START_GCODE_BEGIN")) {
            header = false;
            startCode = true;
            DecimalFormat df = new DecimalFormat("0.##");
            template.append(";Layer Thickness = ").append(data.get("layerHeight")).append("\n");
            template.append(";Number of Slices = ").append(df.format(data.get("totalLayer"))).append("\n");
            template.append(";Z Lift Feed Rate = ").append(data.get("normalLayerLiftSpeed")).append("\n");
            template.append(";Lift Distance = ").append(data.get("normalLayerLiftHeight")).append("\n");
            template.append(";ROW CALCULATED VALUES\n");
            template.append(";image: zero based image/slice index\n");
            template.append(";exposure_time: calculated exposure time for image/slice/layer\n");
            template.append(";rise_dist: calculated rise distance for image/slice/layer (relative)\n");
            template.append(";fall_dist: calculated fall distance for image/slice/layer (relative)\n");
            template.append(";rise_pos: calculated rise position for image/slice/layer (absolute)\n");
            template.append(";fall_pos: calculated fall distance for image/slice/layer (absolute)\n");
            template.append(";rise_speed: calculated rise speed for image/slice/layer\n");
            template.append(";fall_speed: calculated fall speed for image/slice/layer\n");
            template.append(";machine_height: copy of 'machineZ'\n");
            template.append("<#assign machine_height=machineZ>\n");
            template.append(line).append("\n");
          } else if (line.startsWith(";START_GCODE_END")) {
            startCode = false;
            template.append(line).append("\n");
          } else if (startCode || endCode)
            template.append(line).append("\n");
          else if (line.startsWith(";LAYER_START:")) {
            if (line.startsWith(";LAYER_START:0")) {
              printCode = true;
              template.append("<#list slices as slice>\n");
              template.append("<#assign image=slice.image exposure_time=slice.exposure_time rise_dist=slice.rise_dist fall_dist=slice.fall_dist>\n");
              template.append("<#assign rise_pos=slice.rise_pos fall_pos=slice.fall_pos rise_speed=slice.rise_speed fall_speed=slice.fall_speed>\n");
              template.append("<#assign layer=slice.layer fall_pos=slice.fall_pos rise_speed=slice.rise_speed fall_speed=slice.fall_speed peel_delay=slice.peel_delay>\n");
              template.append("\n;LAYER_START:[=layer]\n");
              bottomLayers = (double) data.get("bottomLayerCount");
            }
            printData = true;
            slice = new HashMap<>();
            slices.add(slice);

            double layer = Double.parseDouble(line.substring(line.lastIndexOf(":") + 1));
            double bottomLayerExposureTime  = (double)data.get("bottomLayerExposureTime");
            double normalExposureTime       = (double)data.get("normalExposureTime");
            double bottomLayerLiftHeight    = (double)data.get("bottomLayerLiftHeight");
            double normalLayerLiftHeight    = (double)data.get("normalLayerLiftHeight");
            double layerHeight              = (double)data.get("layerHeight");
            double normalDropSpeed          = (double)data.get("normalDropSpeed");
            double bottomLayerLiftSpeed     = (double)data.get("bottomLayerLiftSpeed");
            double normalLayerLiftSpeed     = (double)data.get("normalLayerLiftSpeed");

            slice.put("image", layer + ".png");
            slice.put("layer", layer);

            slice.put("exposure_time", (layer < bottomLayers)
                    ? bottomLayerExposureTime
                    : normalExposureTime);

            slice.put("rise_dist", (layer < bottomLayers)
                    ? bottomLayerLiftHeight
                    : normalLayerLiftHeight);

            slice.put("fall_dist", (layer < bottomLayers)
                    ? bottomLayerLiftHeight - layerHeight
                    : normalLayerLiftHeight - layerHeight);

            slice.put("rise_pos", (layer < bottomLayers)
                    ? bottomLayerLiftHeight + layer * layerHeight
                    : normalLayerLiftHeight + layer * layerHeight);

            slice.put("fall_pos", (layer + 1) * layerHeight);
            slice.put("rise_speed", (layer < bottomLayers)
                    ? bottomLayerLiftSpeed
                    : normalLayerLiftSpeed);

            slice.put("fall_speed", normalDropSpeed);
            //-->> rise_dist / (rise_speed / 60) * 1000 + fall_dist / (fall_speed/60) * 1000 + 850)
            slice.put("peel_delay", (layer < bottomLayers)
                    ?  Math.round( bottomLayerLiftHeight / (bottomLayerLiftSpeed/60)*1000 + (bottomLayerLiftHeight - layerHeight) / (normalDropSpeed/60) * 1000 + 850)
                    :  Math.round( normalLayerLiftHeight / (normalLayerLiftSpeed/60)*1000 + (normalLayerLiftHeight - layerHeight) / (normalDropSpeed/60) * 1000 + 850));


          } else if (line.startsWith(";END_GCODE_BEGIN")) {
            template.append("\n").append(line).append("\n");
            endCode = true;
          } else if (printData) {
            if (printCode) {
              if (line.startsWith(";currPos:")) {
                template.append(";currPos:[=layer*layerHeight]\n");
              } else {
                template.append(line).append("\n");
                if (line.startsWith(";LAYER_END")) {
                  printCode = false;
                  template.append("</#list>\n");
                }
              }
            }
          } else if (header) {
            if (line.startsWith(";")) {
              template.append(line).append("\n");
              String[] pair = line.split(":", 2);
              try {
                data.put(pair[0].substring(1), Double.parseDouble(pair[1]));
              } catch (NumberFormatException e) {
                try {
                  data.put(pair[0].substring(1), Double.parseDouble(pair[1]));
                } catch (NumberFormatException e1) {
                  data.put(pair[0].substring(1), pair[1]);
                }
              }
            }
          }
        }
        isr.close();
        isr = null;
        if (freemakerTemplate)
          throw new InterruptedException("Interupt for freemaker template print");
        Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        cfg.setDirectoryForTemplateLoading(new File(args[0]));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(false);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
        cfg.setSetting("number_format", "computer");
        new Template("slicer", new StringReader(template.toString()), cfg).process(data, osw);
        osw.flush();
        for (int i = 0; i < (double) data.get("totalLayer"); i++) {
          Path orig = Paths.get(args[0] + (i + 1) + ".png");
          Files.move(orig, orig.resolveSibling(String.format("slice%05d.png", i)));
        }
        Path file = Paths.get(args[0] + "run.gcode");
        Files.delete(file);
        Path sourceFile = Paths.get("nova3d-reference_slice.conf");
        file = Paths.get(args[0] + "slice.conf");
        Files.copy(sourceFile, file, StandardCopyOption.REPLACE_EXISTING );
        file = Paths.get(args[0] + "preview.png");
        Files.move(file, file.resolveSibling("preview_cropping.png"), StandardCopyOption.REPLACE_EXISTING);
        osw.close();
      } catch (Throwable e) {
        OutputStreamWriter logfile = new OutputStreamWriter(new FileOutputStream(args[0] + "errors.log"));
        PrintWriter printWriter = new PrintWriter(logfile);
        e.printStackTrace(printWriter);
        int lineNumber = 1;
        for (String line : template.toString().split("\n"))
          printWriter.write(String.format("/* %3d */ %s%s", lineNumber++, line, "\n"));
        printWriter.flush();
        printWriter.close();
        if (isr != null) isr.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}