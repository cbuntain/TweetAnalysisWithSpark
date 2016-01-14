/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import scala.collection.JavaConverters._
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import twitter4j.json.DataObjectFactory
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat;
import java.util.Locale;


/*
 * Find users who post in two different data sets
 */
object UsersBeforeAndAfter {
  
  // Twitter's time format'
  def TIME_FORMAT = "EEE MMM d HH:mm:ss Z yyyy"

  /**
   * @param args the command line arguments
   */
  def main(args: Array[String]): Unit = {
    
    val conf = new SparkConf().setAppName("Users Before + After")
    val sc = new SparkContext(conf)

    val beforePath = args(0)
    val afterPath = args(1)
    val outputPath = args(2)

    val partSize : Option[Int] = if ( args.length > 3 ) { Some(args(3).toInt) } else { None }

    val beforeUsers = getUsers(beforePath, sc, partSize)
    val afterUsers = getUsers(afterPath, sc, partSize)

    val commonUsers = beforeUsers.intersection(afterUsers)
    println("Number of users in common: " + commonUsers.count())

    commonUsers.saveAsTextFile(outputPath)
  }

  /**
   * Return a set of user IDs given a set of tweets
   *
   * @param dataPath Data to read
   * @param sc The date to convert
   * @param partitionSize An int if we want to repartition the input. Can be none
   */
  def getUsers(dataPath : String, sc : SparkContext, partitionSize : Option[Int]) : RDD[Long] = {

    val twitterMsgsRaw = sc.textFile(dataPath)

    // Repartition if desired using the new partition count
    var twitterMsgs = twitterMsgsRaw
    if (partitionSize.isDefined) {
      val initialPartitions = partitionSize.get
      twitterMsgs = twitterMsgsRaw.repartition(initialPartitions)
    }

    // Convert each JSON line in the file to a status using Twitter4j
    //  Note that not all lines are Status lines, so we catch any exception
    //  generated during this conversion and set to null since we don't care
    //  about non-status lines.'
    val tweets = twitterMsgs.map(line => {
      try {
        DataObjectFactory.createStatus(line)
      } catch {
        case e : Exception => null
      }
    }).filter(status => status != null)

    val users : RDD[Long] = tweets.flatMap(status => {
      if ( status.isRetweet ) {
        List(status.getUser.getId, status.getRetweetedStatus.getUser.getId)
      } else {
        List(status.getUser.getId)
      }
    }).distinct()

    return users
  }

  /**
   * Convert a given date into a string using TIME_FORMAT
   *
   * @param date The date to convert
   */
  def dateFormatter(date : Date) : String = {
    val sdf = new SimpleDateFormat(TIME_FORMAT, Locale.US);
    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

    sdf.format(date)
  }
}
