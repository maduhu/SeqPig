
-- example script.  Modify D= lines to run

-- parameters:
--  * inputpath

set default_parallel 1;

-- speculative execution isn't helping us much, so turn it off
set mapred.map.tasks.speculative.execution false;
set mapred.reduce.tasks.speculative.execution false;

-- Use in-memory aggregation, as possible
set pig.exec.mapPartAgg true;
set pig.exec.mapPartAgg.minReduction 5;

-- new experimental Pig feature -- generates specialized typed classes for the tuples
set pig.schematuple on;

A = load '$inputfile' using FastqUDFLoader();
B = FOREACH A GENERATE sequence, quality;
C = GROUP B ALL;
D = FOREACH C GENERATE BaseCounts(B.$0), BaseQualCounts(B.$1);
E = FOREACH D GENERATE FormatBaseCounts(D.$0), FormatBaseQualCounts(D.$1);
STORE E into '$outputfile';
