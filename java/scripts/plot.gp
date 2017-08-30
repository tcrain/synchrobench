set terminal png size 1600,1200 enhanced font "Helvetica,12"
set output outfile
set key autotitle columnheader
#set style data lines
set auto x
#set yrange [0:300000]
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set boxwidth 0.9
set xlabel xlab
set title tit
set ylabel "Throughput tx/sec"
#plot for [i=2:algs] filename using 0:i:xtic(1)
plot for [i=2:algs] filename using i:xtic(1)
