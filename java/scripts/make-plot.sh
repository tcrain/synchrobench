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

OutFileUpdate="./results/update-$OutFile"
OutFileThread="./results/thread-$OutFile"

AlgNum=$((`echo $Classes | wc -w` + 1))

for MinSize in ${Sizes[@]}
do
    MaxSize=$(($MinSize * 2))
    ############# Plot per thread results ############################
    for U in ${Updates[@]}
    do
	Filename="${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	# Make the 2nd line not a comment
	sed -e '2s/^.\{2\}//' ${Filename} > tmp
	gnuplot -e "filename='tmp'; algs='${AlgNum}'; outfile='${Filename}.png'; xlab=\"Num threads\"; tit=\"${Filename}\"" plot.gp
    done

    ############### Plot per update ratio results ####################
    for Threads in ${ThreadNums[@]}
    do
	Filename="${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	sed -e '2s/^.\{2\}//' ${Filename} > tmp
	gnuplot -e "filename='tmp'; algs='${AlgNum}'; outfile='${Filename}.png'; xlab=\"Update\"; tit=\"${Filename}\"" plot.gp
    done
done
