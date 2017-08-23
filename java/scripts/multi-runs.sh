#!/bin/bash

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

#Updates=(5 10 20 50 100)
#Updates=(0 100)

mkdir -p results

Timestamp=`date +%s`
Log="./results/${Timestamp}.log"

echo "##########################" | tee -a "$Log"
echo "# Running bench with config:" | tee -a "$Log"
echo "# Classes: $Classes" | tee -a "$Log"
echo "# Jars: $Jars" | tee -a "$Log"
echo "# Sizes: $Sizes" | tee -a "$Log"
echo "# Thread nums: $ThreadNums" | tee -a "$Log"
echo "# Updates: $Updates" | tee -a "$Log"
echo "# Iterations: $Runs" | tee -a "$Log"
echo "# Outfile: $OutFile" | tee -a "$Log"
echo "# Log: $Log" | tee -a "$Log"
echo "############################" | tee -a "$Log"


OutFileUpdate="./results/update-$OutFile"
OutFileThread="./results/thread-$OutFile"

for MinSize in ${Sizes[@]}
do
    MaxSize=$(($MinSize * 2))

    ############# Setup thread info results file ############################
    for U in ${Updates[@]}
    do
	echo "# -i $MinSize -r $MaxSize -t $Threads -runs $Runs" >> "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	echo -ne "# num_threads\t" >> "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	for Jar in ${Jars[@]}
	do
	    echo -ne "${Jar}-" >> "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	    for Class in ${Classes[@]}
	    do
		echo -ne "${Class}\t" >> "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	    done
	done
	
	#echo -ne "skiplists.versioned.VersionedTower2\t" >> $OutFile$MaxSize
	echo -ne "\n" >> "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	line=1
	for Threads in ${ThreadNums[@]}
	do
	    line=$((line + 1))
	    echo -e "${Threads}\t" >> "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
	done
    done
    ################ Done setup thread info results file #####################

    line=2
    for Threads in ${ThreadNums[@]}
    do
	line=$((line + 1))
	echo "# -i $MinSize -r $MaxSize -t $Threads -runs $Runs" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	echo -ne "# update_percent\t" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	for Jar in ${Jars[@]}
	do
	    echo -ne "${Jar}-" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	    for Class in ${Classes[@]}
	    do
		echo -ne "${Class}\t" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	    done
	done
	
	#echo -ne "skiplists.versioned.VersionedTower2\t" >> $OutFile$MaxSize
	echo -ne "\n" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
		
	for U in ${Updates[@]}
	do
	    echo -ne "${U}\t" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	    for Jar in ${Jars[@]}
	    do
		for Class in ${Classes[@]}
		do
		    Total=0
		    for i in $(seq 1 $Runs)
		    do
			java -jar $Jar -b $Class -i $MinSize -r $MaxSize -t $Threads -u $U | tee tmp.txt >> "$Log"
			Value=`awk '/Throughput/{print $3}' tmp.txt`
			Value=`echo ${Value} | sed -e 's/[eE]+*/\\*10\\^/'`
			Total=`echo ${Value} + ${Total} | bc`
		    done
		    Avg=`echo ${Total} / ${Runs} | bc`
		    echo Average: $Avg | tee -a "$Log"
		    echo -ne "$Avg\t" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
		    sed -i ' '"$line"'s/$/'"${Avg}\t"'/' "${OutFileThread}-r${MaxSize}-u${U}--${Timestamp}"
		done
	    done
	    
	    echo -ne "\n" >> "${OutFileUpdate}-r${MaxSize}-t${Threads}--${Timestamp}"
	done
    done
done

echo "############ !Benchmark done! ################" | tee -a "$Log"
