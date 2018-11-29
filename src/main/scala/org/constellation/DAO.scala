package org.constellation

import akka.stream.ActorMaterializer
import better.files.File
import com.typesafe.scalalogging.Logger
import org.constellation.primitives._
import constellation.SHA256Ext

class DAO extends NodeData
  with Reputation
  with PeerInfoUDP
  with Genesis
  with EdgeDAO {

  val logger = Logger(s"Data")

  var actorMaterializer: ActorMaterializer = _

  var confirmWindow : Int = 30

  var transactionAcceptedAfterDownload: Long = 0L
  var downloadFinishedTime: Long = System.currentTimeMillis()

  var preventLocalhostAsPeer: Boolean = true

  def idDir = File(s"tmp/${id.medium}")

  def dbPath: File = {
    val f = File(s"tmp/${id.medium}/db")
    f.createDirectoryIfNotExists()
    f
  }

  def snapshotPath: File = {
    val f = File(s"tmp/${id.medium}/snapshots")
    f.createDirectoryIfNotExists()
    f
  }

  def snapshotHashes: Seq[String] = {
    snapshotPath.list.toSeq.map{_.name}
  }

  def peersInfoPath: File = {
    val f = File(s"tmp/${id.medium}/peers")
    f
  }

  def seedsPath: File = {
    val f = File(s"tmp/${id.medium}/seeds")
    f
  }

  // don't use for testing, assign manually
  def addressPartition(address: String): Int = {
    BigInt(address.sha256, 16) % processingConfig.numPartitions
  }.toInt

  def selfAddressPartition: Int = {
    val res = addressPartition(selfAddressStr)
    metricsManager ! UpdateMetric("selfAddressPartition", res.toString)
    res
  }

  def restartNode(): Unit = {
    downloadMode = true
    signedPeerLookup.clear()
    peersAwaitingAuthenticationToNumAttempts.clear()
    signedPeerLookup.clear()
    deadPeers = Seq()
  }

}
