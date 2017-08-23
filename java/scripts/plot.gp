set terminal png size 1600,1200 enhanced font "Helvetica,12"
set output outfile
set key autotitle columnheader
set style data lines
plot for [i=2:algs] filename using 1:i
