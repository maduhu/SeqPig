#!/bin/bash

# script for adding a header and footer to bam file created by BamUDFStorer

if [ $# -lt 2 ]
then
        echo "error: usage $0 <outputfile.sam> <original_source.sam>"
        exit 0
fi

source "${SEQPIG_HOME}/bin/seqpigEnv.sh"

bamoutputfilename="$1";
baminputfilename="$2"; # required for sam file header

rm -f $bamoutputfilename

${HADOOP} fs -getmerge ${1} ${1}

if [ -e "./$bamoutputfilename" ]
then
        echo "writing to file $bamoutputfilename";

        cat $bamoutputfilename > tmphdr

	# NOTE: adding the terminator seems to cause problems
	# when later importing again.
        #if [ -e "${SEQPIG_HOME}/data/bgzf-terminator.bin" ]
        #then
        	#cat ${SEQPIG_HOME}/data/bgzf-terminator.bin >> tmphdr
                mv tmphdr ${1}
        #else
        #        echo "error: cannot find bgzf-terminator.bin"
        #fi
else
        echo "error: could not find $bamoutputfilename in HDFS!";
fi

if [ -e ".${bamoutputfilename}.crc" ]
then
        echo "removing (now incorrect) hadoop checksum to allow later import";
        rm -f .${bamoutputfilename}.crc
fi
