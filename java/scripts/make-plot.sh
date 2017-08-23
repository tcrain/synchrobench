#!/bin/bash

Timestamp=$2
OldIFS="$IFS"
IFS=$'\n' Config=($(<$1))

Runs=${Config[1]}
#Jar=${Config[2]}
OutFile=${Config[5]}

IFS=$' \t\r\n'
Classes=$(head -n 1 $1)
Jars=$(sed -n '3p' < $1)
Sizes=$(sed -n '4p' < $1)
ThreadNums=$(sed -n '5p' < $1)
Updates=$(sed -n '7p' < $1)

IFS="$OldIFS"

AlgNum=${#Classes[@]}

for MinSize in ${Sizes[@]}
do
    ############# Plot per thread results ############################
    for U in ${Updates[@]}
    do
	Filename="${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	gnuplot -e "filename='${Filename}'; algs='${AlgNum}'; outfile='${Filename}.png'" plot.gp
    done

    ############### Plot per update ratio results ####################
    for Threads in ${ThreadNums[@]}
    do
	Filename="${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	gnuplot -e "filename='${Filename}'; algs='${AlgNum}'; outfile='${Filename}.png'" plot.gp
    done
done
