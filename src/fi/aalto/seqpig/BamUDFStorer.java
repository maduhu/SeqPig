// Copyright (c) 2012 Aalto University
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to
// deal in the Software without restriction, including without limitation the
// rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
// sell copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
// IN THE SOFTWARE.

package fi.aalto.seqpig;

import org.apache.pig.StoreFunc;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.data.Tuple; 
import org.apache.pig.data.DataByteArray;
import org.apache.pig.data.DataType;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.PigException;
import org.apache.pig.ResourceSchema;
import org.apache.pig.impl.util.UDFContext;
//import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigSplit;
//import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigTextInputFormat;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.RecordWriter; 
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
//import org.apache.hadoop.mapred.TextInputFormat;
//import org.apache.hadoop.io.Text;

import fi.tkk.ics.hadoop.bam.BAMOutputFormat;
import fi.tkk.ics.hadoop.bam.BAMRecordWriter;
//import fi.tkk.ics.hadoop.bam.SplittingBAMIndex;
import fi.tkk.ics.hadoop.bam.SAMRecordWritable;
import fi.tkk.ics.hadoop.bam.KeyIgnoringBAMRecordWriter;
import fi.tkk.ics.hadoop.bam.KeyIgnoringBAMOutputFormat;
import fi.tkk.ics.hadoop.bam.custom.samtools.SAMRecord;
//import fi.tkk.ics.hadoop.bam.FileVirtualSplit;
import fi.tkk.ics.hadoop.bam.custom.samtools.SAMFileHeader;
import fi.tkk.ics.hadoop.bam.custom.samtools.SAMTextHeaderCodec;
import fi.tkk.ics.hadoop.bam.custom.samtools.SAMTagUtil;
import fi.tkk.ics.hadoop.bam.custom.samtools.SAMReadGroupRecord;
import fi.tkk.ics.hadoop.bam.custom.samtools.SAMProgramRecord;

import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.util.StringLineReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
//import java.util.Iterator;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.URI;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class BamUDFStorer extends StoreFunc {
    protected RecordWriter writer = null;
    protected String samfileheader = null;
    protected SAMFileHeader samfileheader_decoded = null;
    private static final int BUFFER_SIZE = 1024;

    protected HashMap<String,Integer> selectedBAMAttributes = null;
    protected HashMap<String,Integer> allBAMFieldNames = null;

    public BamUDFStorer() {
	System.out.println("WARNING: noarg BamUDFStorer() constructor!");
        decodeSAMFileHeader();
    }

    public BamUDFStorer(String samfileheaderfilename) {

	String str = "";
	this.samfileheader = "";

	// WARNING: samfileheaderfilename has to be local file!

	try {
	    //BufferedReader in = new BufferedReader(new FileReader(samfileheaderfilename));

	    Configuration conf = UDFContext.getUDFContext().getJobConf();
	    
	    if(conf == null) {
		decodeSAMFileHeader();
		return;
	    }

            FileSystem fs;

	   try {
	    if(FileSystem.getDefaultUri(conf) == null
		|| FileSystem.getDefaultUri(conf).toString() == "")
		fs = FileSystem.get(new URI("hdfs://"), conf);
	    else 
	    	fs = FileSystem.get(conf);
	    } catch (Exception e) {
		fs = FileSystem.get(new URI("hdfs://"), conf);
            	System.out.println("WARNING: problems with filesystem config?");
            	System.out.println("exception was: "+e.toString());
            }

	    BufferedReader in = new BufferedReader(new InputStreamReader(fs.open(new Path(fs.getHomeDirectory(), new Path(samfileheaderfilename)))));

	    while(true) {
		str = in.readLine();

		if(str == null) break;
		else
		    this.samfileheader += str + "\n";
	    }

	    in.close();
	} catch (Exception e) {
	    System.out.println("ERROR: could not read BAM header from file "+samfileheaderfilename);
	    System.out.println("exception was: "+e.toString());
	}

	//this.samfileheader = _samfileheader;
	/*String[] header = str.split("\\t");
	this.samfileheader = "";

	for(int i=0;i<header.length;i++) {
	    this.samfileheader += header[i] + "\n";
	}

	header = this.samfileheader.split("_XTABX_");
	this.samfileheader = "";

	//System.out.println("Loaded SAMFileHeader:");

	for(int i=0;i<header.length;i++) {
	    this.samfileheader += header[i] + "\t";
	    //System.out.println(header[i]);
	    }*/

	try {
	    BASE64Encoder encode = new BASE64Encoder();
	    Properties p = UDFContext.getUDFContext().getUDFProperties(this.getClass());

	    ByteArrayOutputStream bstream = new ByteArrayOutputStream();
	    ObjectOutputStream ostream = new ObjectOutputStream(bstream);
	    ostream.writeObject(this.samfileheader);
	    ostream.close();
	    String datastr = encode.encode(bstream.toByteArray());
	    p.setProperty("samfileheader", datastr);
	} catch (Exception e) {
	    //throw new IOException(e);
	    System.out.println("ERROR: Unable to store SAMFileHeader in BamUDFStorer!");
	}

	this.samfileheader_decoded = getSAMFileHeader();
	
	//System.err.println("samfileheader: "+this.samfileheader);
    }

    protected void decodeSAMFileHeader(){
	try {
            BASE64Decoder decode = new BASE64Decoder();
            Properties p = UDFContext.getUDFContext().getUDFProperties(this.getClass());
            String datastr;

            datastr = p.getProperty("samfileheader");
            byte[] buffer = decode.decodeBuffer(datastr);
            ByteArrayInputStream bstream = new ByteArrayInputStream(buffer);
            ObjectInputStream ostream = new ObjectInputStream(bstream);

            this.samfileheader = (String)ostream.readObject();
        } catch (Exception e) {
            //throw new IOException(e);
            //System.out.println("ERROR: No SAMFileHeader found in BamUDFStorer!");
        }

        this.samfileheader_decoded = getSAMFileHeader();
    }

    @Override
    public void putNext(Tuple f) throws IOException {

	//HashMap<String,Integer> selectedBAMAttributes;
	//HashMap<String,Integer> allBAMFieldNames;

	if(selectedBAMAttributes == null || allBAMFieldNames == null) {
	    try {
		BASE64Decoder decode = new BASE64Decoder();
		Properties p = UDFContext.getUDFContext().getUDFProperties(this.getClass());
		String datastr;
		//byte[] buffer = ((String)p.getProperty("tuplefields")).getBytes("UTF8");
		
		datastr = p.getProperty("selectedBAMAttributes");
		byte[] buffer = decode.decodeBuffer(datastr);
		ByteArrayInputStream bstream = new ByteArrayInputStream(buffer);
		ObjectInputStream ostream = new ObjectInputStream(bstream);
		
		selectedBAMAttributes =
		    (HashMap<String,Integer>)ostream.readObject();

		datastr = p.getProperty("allBAMFieldNames");
		buffer = decode.decodeBuffer(datastr);
		bstream = new ByteArrayInputStream(buffer);
		ostream = new ObjectInputStream(bstream);

		allBAMFieldNames =
		    (HashMap<String,Integer>)ostream.readObject();
	    } catch (ClassNotFoundException e) {
		throw new IOException(e);
	    }

	    //ostream.close();
	    //bstream.close();
	}

	SAMRecordWritable samrecwrite = new SAMRecordWritable();
	SAMRecord samrec = new SAMRecord(samfileheader_decoded);

	//System.out.println("tuple size: "+f.size());

	/*if(f.size() > 0 && DataType.findType(f.get(0)) == DataType.CHARARRAY) {
	    samrec.setReadName((String)f.get(0));
	    System.out.println("name: "+(String)f.get(0));
	}

	if(f.size() > 1 && DataType.findType(f.get(1)) == DataType.INTEGER) {
	    samrec.setAlignmentStart(((Integer)f.get(1)).intValue());
	    System.out.println("start: "+((Integer)f.get(1)).intValue());
	}

	System.out.println("end: "+((Integer)f.get(2)).intValue());
	// TODO: check why setAlignmentEnd not supported

	if(f.size() > 3 && DataType.findType(f.get(3)) == DataType.CHARARRAY) {
	    samrec.setReadString((String)f.get(3));
	    System.out.println("read: "+(String)f.get(3));
	    
	}

	if(f.size() > 4 && DataType.findType(f.get(4)) == DataType.CHARARRAY) {
	    samrec.setCigarString((String)f.get(4));
	    System.out.println("cigar: "+(String)f.get(4));
	}

	if(f.size() > 5 && DataType.findType(f.get(5)) == DataType.CHARARRAY) {
	    samrec.setBaseQualityString((String)f.get(5));
	    System.out.println("quality: "+(String)f.get(5));
	}

	if(f.size() > 6 && DataType.findType(f.get(6)) == DataType.INTEGER) {
	    samrec.setFlags(((Integer)f.get(6)).intValue());
	    System.out.println("flags: "+((Integer)f.get(6)).intValue());
	}
	
	if(f.size() > 7 && DataType.findType(f.get(7)) == DataType.INTEGER) {
	    samrec.setInferredInsertSize(((Integer)f.get(7)).intValue());
	    System.out.println("inferredInsertSize: "+((Integer)f.get(7)).intValue());
	}
	
	if(f.size() > 8 && DataType.findType(f.get(8)) == DataType.INTEGER) {
	    samrec.setMappingQuality(((Integer)f.get(8)).intValue());
	    System.out.println("mappingQuality: "+((Integer)f.get(8)).intValue());
	}

	if(f.size() > 9 && DataType.findType(f.get(9)) == DataType.INTEGER) {
	    samrec.setMateAlignmentStart(((Integer)f.get(9)).intValue());
	    System.out.println("mateAlignmentStart: "+((Integer)f.get(9)).intValue());
	}

	if(f.size() > 10 && DataType.findType(f.get(10)) == DataType.INTEGER) {
	    samrec.setIndexingBin((Integer)f.get(10));
	    System.out.println("indexingBin: "+((Integer)f.get(10)).intValue());
	}

	if(f.size() > 11 && DataType.findType(f.get(11)) == DataType.INTEGER) {
	    samrec.setMateReferenceIndex(((Integer)f.get(11)).intValue());
	    System.out.println("mateReferenceIndex: "+((Integer)f.get(11)).intValue());
	}

	if(f.size() > 12 && DataType.findType(f.get(12)) == DataType.INTEGER) {
	    samrec.setReferenceIndex(((Integer)f.get(12)).intValue());
	    System.out.println("referenceIndex: "+((Integer)f.get(12)).intValue());
	}

	if(f.size() > 13 && DataType.findType(f.get(13)) == DataType.CHARARRAY) {
	    //samrec.setAttribute("PG", new SAMProgramRecord((String)f.get(13)));
	    samrec.setAttribute("PG", f.get(13));
	    System.out.println("programGroup: "+((String)f.get(13)));
	}

	if(f.size() > 14 && DataType.findType(f.get(14)) == DataType.CHARARRAY) {
	    //samrec.setAttribute("RG", new SAMReadGroupRecord((String)f.get(14)));
	    samrec.setAttribute("RG", f.get(14));
	    System.out.println("readGroup: "+((String)f.get(14)));
	}

	if(f.size() > 15 && DataType.findType(f.get(15)) == DataType.CHARARRAY) {
	    //samrec.setAttribute("RG", new SAMReadGroupRecord((String)f.get(14)));
	    samrec.setAttribute("SM", f.get(15));
	    System.out.println("sm: "+((String)f.get(15)));
	    }*/

	int index = getFieldIndex("name", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.CHARARRAY) {
	    samrec.setReadName((String)f.get(index));
	    //System.out.println("name: "+(String)f.get(index));
	}

	index = getFieldIndex("start", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setAlignmentStart(((Integer)f.get(index)).intValue());
	    //System.out.println("start: "+((Integer)f.get(index)).intValue());
	}

	//index = getFieldIndex("end", allBAMFieldNames);
	//System.out.println("end: "+((Integer)f.get(index)).intValue());
	// TODO: check why setAlignmentEnd not supported

	index = getFieldIndex("read", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.CHARARRAY) {
	    samrec.setReadString((String)f.get(index));
	    //System.out.println("read: "+(String)f.get(index));
	    
	}

	index = getFieldIndex("cigar", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.CHARARRAY) {
	    samrec.setCigarString((String)f.get(index));
	    //System.out.println("cigar: "+(String)f.get(index));
	}

	index = getFieldIndex("basequal", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.CHARARRAY) {
	    samrec.setBaseQualityString((String)f.get(index));
	    //System.out.println("basequal: "+(String)f.get(index));
	}

	index = getFieldIndex("flags", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setFlags(((Integer)f.get(index)).intValue());
	    //System.out.println("flags: "+((Integer)f.get(index)).intValue());
	}
	
	index = getFieldIndex("insertsize", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setInferredInsertSize(((Integer)f.get(index)).intValue());
	    //System.out.println("inferredInsertSize: "+((Integer)f.get(index)).intValue());
	}
	
	index = getFieldIndex("mapqual", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setMappingQuality(((Integer)f.get(index)).intValue());
	    //System.out.println("mappingQuality: "+((Integer)f.get(index)).intValue());
	}

	index = getFieldIndex("matestart", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setMateAlignmentStart(((Integer)f.get(index)).intValue());
	    //System.out.println("mateAlignmentStart: "+((Integer)f.get(index)).intValue());
	}

	index = getFieldIndex("indexbin", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setIndexingBin((Integer)f.get(index));
	    //System.out.println("indexingBin: "+((Integer)f.get(index)).intValue());
	}

	index = getFieldIndex("materefindex", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setMateReferenceIndex(((Integer)f.get(index)).intValue());
	    //System.out.println("mateReferenceIndex: "+((Integer)f.get(index)).intValue());
	}

	index = getFieldIndex("refindex", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.INTEGER) {
	    samrec.setReferenceIndex(((Integer)f.get(index)).intValue());
	    //System.out.println("referenceIndex: "+((Integer)f.get(index)).intValue());
	}

	//Iterator<Map.Entry<String,Integer> > it = selectedBAMAttributes.entrySet().iterator();
	/*Set<Map.Entry<String, Integer>> set = selectedBAMAttributes.entrySet();

	for (Map.Entry<String, Integer> pairs : set) {
	
	//while (it.hasNext()) {
	    //Map.Entry<String,Integer> pairs =
	    //	(Map.Entry<String,Integer>)it.next();
	    //System.out.println(pairs.getKey() + " = " + pairs.getValue());
	    index = pairs.getValue().intValue();
	    String attribute = pairs.getKey();

	    samrec.setAttribute(attribute.toUpperCase(), f.get(index));
	    //System.out.println(attribute+": "+((String)f.get(index)));
	    }*/

	index = getFieldIndex("attributes", allBAMFieldNames);
	if(index > -1 && DataType.findType(f.get(index)) == DataType.MAP) {
	    Set<Map.Entry<String, Object>> set = ((HashMap<String, Object>)f.get(index)).entrySet();

	    for (Map.Entry<String, Object> pairs : set) {
		String attributeName = pairs.getKey();

		samrec.setAttribute(attributeName.toUpperCase(), pairs.getValue());
		//System.out.println("set attribute: "+attributeName+" ("+pairs.getValue().getClass().getName()+")");
	    }

	    //samrec.setReferenceIndex(((Integer)f.get(index)).intValue());
	    //System.out.println("referenceIndex: "+((Integer)f.get(index)).intValue());
	}
	
	samrec.hashCode(); // causes eagerDecode()
	samrecwrite.set(samrec);

	try {
            writer.write(null, samrecwrite);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private int getFieldIndex(String field, HashMap<String,Integer> fieldNames) {
	if(!fieldNames.containsKey(field)) {
	    System.err.println("Warning: field missing: "+field);
	    return -1;
	}

	return ((Integer)fieldNames.get(field)).intValue();
    }

    @Override
    public void checkSchema(ResourceSchema s) throws IOException {

	selectedBAMAttributes = new HashMap<String,Integer>();
	allBAMFieldNames = new HashMap<String,Integer>();
	String[] fieldNames = s.fieldNames();

	for(int i=0;i<fieldNames.length;i++) {
	    System.out.println("field: "+fieldNames[i]);
	    allBAMFieldNames.put(fieldNames[i], new Integer(i));

	    if(fieldNames[i].equalsIgnoreCase("RG")
	       || fieldNames[i].equalsIgnoreCase("LB")
	       || fieldNames[i].equalsIgnoreCase("PU")
	       || fieldNames[i].equalsIgnoreCase("PG")
	       || fieldNames[i].equalsIgnoreCase("AS")
	       || fieldNames[i].equalsIgnoreCase("SQ")
	       || fieldNames[i].equalsIgnoreCase("MQ")
	       || fieldNames[i].equalsIgnoreCase("NM")
	       || fieldNames[i].equalsIgnoreCase("H0")
	       || fieldNames[i].equalsIgnoreCase("H1")
	       || fieldNames[i].equalsIgnoreCase("H2")
	       || fieldNames[i].equalsIgnoreCase("UQ")
	       || fieldNames[i].equalsIgnoreCase("PQ")
	       || fieldNames[i].equalsIgnoreCase("NH")
	       || fieldNames[i].equalsIgnoreCase("IH")
	       || fieldNames[i].equalsIgnoreCase("HI")
	       || fieldNames[i].equalsIgnoreCase("MD")
	       || fieldNames[i].equalsIgnoreCase("CS")
	       || fieldNames[i].equalsIgnoreCase("CQ")
	       || fieldNames[i].equalsIgnoreCase("CM")
	       || fieldNames[i].equalsIgnoreCase("R2")
	       || fieldNames[i].equalsIgnoreCase("Q2")
	       || fieldNames[i].equalsIgnoreCase("S2")
	       || fieldNames[i].equalsIgnoreCase("CC")
	       || fieldNames[i].equalsIgnoreCase("CP")
	       || fieldNames[i].equalsIgnoreCase("SM")
	       || fieldNames[i].equalsIgnoreCase("AM")
	       || fieldNames[i].equalsIgnoreCase("MF")
	       || fieldNames[i].equalsIgnoreCase("E2")
	       || fieldNames[i].equalsIgnoreCase("U2")
	       || fieldNames[i].equalsIgnoreCase("OQ")) {

		System.out.println("selected attribute: "+fieldNames[i]+" i: "+i);
		selectedBAMAttributes.put(fieldNames[i], new Integer(i));
	    }
	}

	if(!( allBAMFieldNames.containsKey("name")
	      && allBAMFieldNames.containsKey("start")
	      && allBAMFieldNames.containsKey("end")
	      && allBAMFieldNames.containsKey("read")
	      && allBAMFieldNames.containsKey("cigar")
	      && allBAMFieldNames.containsKey("basequal")
	      && allBAMFieldNames.containsKey("flags")
	      && allBAMFieldNames.containsKey("insertsize")
	      && allBAMFieldNames.containsKey("mapqual")
	      && allBAMFieldNames.containsKey("matestart")
	      && allBAMFieldNames.containsKey("indexbin")
	      && allBAMFieldNames.containsKey("materefindex")
	      && allBAMFieldNames.containsKey("refindex")))
	    throw new IOException("Error: Incorrect BAM tuple-field name or compulsory field missing");

	BASE64Encoder encode = new BASE64Encoder();
	Properties p = UDFContext.getUDFContext().getUDFProperties(this.getClass());
	String datastr;
	//p.setProperty("someproperty", "value");

	ByteArrayOutputStream bstream = new ByteArrayOutputStream();
	ObjectOutputStream ostream = new ObjectOutputStream(bstream);
	ostream.writeObject(selectedBAMAttributes);
	ostream.close();
	datastr = encode.encode(bstream.toByteArray());
	p.setProperty("selectedBAMAttributes", datastr); //new String(bstream.toByteArray(), "UTF8"));

	bstream = new ByteArrayOutputStream();
	ostream = new ObjectOutputStream(bstream);
	ostream.writeObject(allBAMFieldNames);
	ostream.close();
	datastr = encode.encode(bstream.toByteArray());
	p.setProperty("allBAMFieldNames", datastr); //new String(bstream.toByteArray(), "UTF8"));
    }

    private SAMFileHeader getSAMFileHeader() {
	final SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
	codec.setValidationStringency(ValidationStringency.SILENT);
	return codec.decode(new StringLineReader(this.samfileheader), "SAMFileHeader.clone");
    }

    /*@SuppressWarnings("unchecked")
    private void putField(Object field) throws IOException {
        //string constants for each delimiter
        String tupleBeginDelim = "(";
        String tupleEndDelim = ")";
        String bagBeginDelim = "{";
        String bagEndDelim = "}";
        String mapBeginDelim = "[";
        String mapEndDelim = "]";
        String fieldDelim = ",";
        String mapKeyValueDelim = "#";

        switch (DataType.findType(field)) {
        case DataType.NULL:
            break; // just leave it empty

        case DataType.BOOLEAN:
            mOut.write(((Boolean)field).toString().getBytes());
            break;

        case DataType.INTEGER:
            mOut.write(((Integer)field).toString().getBytes());
            break;

        case DataType.LONG:
            mOut.write(((Long)field).toString().getBytes());
            break;

        case DataType.FLOAT:
            mOut.write(((Float)field).toString().getBytes());
            break;

        case DataType.DOUBLE:
            mOut.write(((Double)field).toString().getBytes());
            break;

        case DataType.BYTEARRAY: {
            byte[] b = ((DataByteArray)field).get();
            mOut.write(b, 0, b.length);
            break;
                                 }

        case DataType.CHARARRAY:
            // oddly enough, writeBytes writes a string
            mOut.write(((String)field).getBytes(UTF8));
            break;

        case DataType.MAP:
            boolean mapHasNext = false;
            Map<String, Object> m = (Map<String, Object>)field;
            mOut.write(mapBeginDelim.getBytes(UTF8));
            for(Map.Entry<String, Object> e: m.entrySet()) {
                if(mapHasNext) {
                    mOut.write(fieldDelim.getBytes(UTF8));
                } else {
                    mapHasNext = true;
                }
                putField(e.getKey());
                mOut.write(mapKeyValueDelim.getBytes(UTF8));
                putField(e.getValue());
            }
            mOut.write(mapEndDelim.getBytes(UTF8));
            break;

        case DataType.TUPLE:
            boolean tupleHasNext = false;
            Tuple t = (Tuple)field;
            mOut.write(tupleBeginDelim.getBytes(UTF8));
            for(int i = 0; i < t.size(); ++i) {
                if(tupleHasNext) {
                    mOut.write(fieldDelim.getBytes(UTF8));
                } else {
                    tupleHasNext = true;
                }
                try {
                    putField(t.get(i));
                } catch (ExecException ee) {
                    throw ee;
                }
            }
            mOut.write(tupleEndDelim.getBytes(UTF8));
            break;

        case DataType.BAG:
            boolean bagHasNext = false;
            mOut.write(bagBeginDelim.getBytes(UTF8));
            Iterator<Tuple> tupleIter = ((DataBag)field).iterator();
            while(tupleIter.hasNext()) {
                if(bagHasNext) {
                    mOut.write(fieldDelim.getBytes(UTF8));
                } else {
                    bagHasNext = true;
                }
                putField((Object)tupleIter.next());
            }
            mOut.write(bagEndDelim.getBytes(UTF8));
            break;

        default: {
            int errCode = 2108;
            String msg = "Could not determine data type of field: " + field;
            throw new ExecException(msg, errCode, PigException.BUG);
        }

        }
	}*/

    @Override
    public OutputFormat getOutputFormat() {
	KeyIgnoringBAMOutputFormat outputFormat = new KeyIgnoringBAMOutputFormat();
	outputFormat.setSAMHeader(getSAMFileHeader());
	return outputFormat;
    }

    @Override
    public void prepareToWrite(RecordWriter writer) {
        this.writer = writer;
    }

    @Override
    public void setStoreLocation(String location, Job job) throws IOException {
        FileOutputFormat.setOutputPath(job, new Path(location));
    }
}