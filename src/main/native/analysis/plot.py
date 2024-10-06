import pandas as pd
from matplotlib import pyplot as plt

from scipy.stats import chisquare

if __name__ == '__main__':
    plt.rcParams["figure.figsize"] = [7.00, 3.50]
    plt.rcParams["figure.autolayout"] = True
    columns = ["Bucket", "Frequency"]
    df = pd.read_csv("dist.csv", names=columns)
    sum = df.Frequency.values.sum()
    dist, pvalue = chisquare(df.Frequency.values)
    print(pvalue)
    # print("Contents in csv file:", df)
    plt.ylim(0, 20000)
    plt.title("Proof distribution") 
    plt.xlabel("Buckets") 
    plt.ylabel("Frequency") 
    plt.plot(df.Bucket, df.Frequency, '.')
    plt.axhline(y = sum/1000, color = 'r', linestyle = '-')
    # plt.show()
    plt.savefig("distribution.pdf", format="pdf")
    # print(df.Frequency.values)