/*
 * #%L
 * KymographBuilder: Yet Another Kymograph Fiji plugin
 * %%
 * Copyright (C) 2016 Hadrien Mary
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package fiji.plugin.kymographbuilder;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.Context;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

/**
 *
 * @author Hadrien Mary
 */
public class KymographFactory {

    public static AtomicInteger IDcounter = new AtomicInteger(-1);
    private final int ID;

    @Parameter
    private Context context;

    @Parameter
    private DatasetService dsService;

    @Parameter
    private LogService log;

    @Parameter
    private ConvertService convert;

    @Parameter
    private UIService ui;

    private final Dataset dataset;
    private Dataset kymograph;

    private final Roi roi;
    private final List<Integer> channelsUsed;

    public KymographFactory(Context context, Dataset dataset,
            Roi roi, List<Integer> channelsUsed) {
        context.inject(this);
        this.channelsUsed = channelsUsed;
        this.roi = roi;
        this.dataset = dataset;

        this.ID = IDcounter.incrementAndGet();
    }

    public Dataset getKymograph() {
        return this.kymograph;
    }

    public void build() {
        // Build lines from the ROI
        LinesBuilder linesBuilder = new LinesBuilder(this.roi);
        linesBuilder.build();

        log.info(linesBuilder.getLines().size() + " lines with a width of "
                + linesBuilder.getlineWidth() + " will be used for the kymograph " + this.ID + ".");

        List<Dataset> kymographs = new ArrayList<>();

        // Init kymo creator for each channels and build kymos
        for (Integer i : this.channelsUsed) {

            log.info("Creating kymograph for the channel " + i + ".");
            KymographCreator creator = new KymographCreator(this.context, this.dataset,
                    i, linesBuilder);

            creator.build();
            kymographs.add(creator.getProjectedKymograph());
        }

        if (this.channelsUsed.size() == 1) {
            this.kymograph = kymographs.get(0);
        } else {
            this.mergeDataset(kymographs);
        }

        // Put back the original Roi object 
        ImagePlus imp = IJ.getImage();
        imp.setRoi(this.roi);
    }

    public <T extends RealType<T>> void mergeDataset(List<Dataset> kymographs) {

        log.info("Merging kymographs for all channels now.");
        
        List<RandomAccessibleInterval<T>> accessibles = new ArrayList();
        kymographs.stream().forEach((kymo) -> {
            accessibles.add((RandomAccessibleInterval<T>) kymo);
        });

        // Stack a list of RandomAccessibleInterval
        RandomAccessibleInterval<T> re = Views.stack(accessibles);

        // Create the merged kymograph
        this.kymograph = dsService.create(re);
        this.kymograph.setName(kymographs.get(0).getName());
        
        // Make the third axis (index = 2) to CHANNEL type.
        // I don't what control exactly the AxisType used in Views.stack
        this.kymograph.axis(2).setType(Axes.CHANNEL);
    }

}
