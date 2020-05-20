package de.farberg.storm.examples;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

public class StormWordCount {

	public static class SplitSentence extends BaseBasicBolt {
		private static final long serialVersionUID = 1L;

		@Override
		public void execute(Tuple input, BasicOutputCollector collector) {
			Arrays.stream(input.getString(0).split(" ")).
			forEach(word -> collector.emit(new Values(word)));
		}

		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("word"));
		}
	}

	public static class WordCount extends BaseBasicBolt {
		private static final long serialVersionUID = 1L;
		private Map<String, Integer> counts = new HashMap<String, Integer>();

		@Override
		public void execute(Tuple tuple, BasicOutputCollector collector) {
			String word = tuple.getString(0);
			Integer count = counts.get(word);

			if (count == null)
				count = 0;

			count++;
			counts.put(word, count);
			collector.emit(new Values(word, count));
		}

		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("word", "count"));
		}
	}

	public static class StormRandomSentenceSpout extends BaseRichSpout {
		private static final long serialVersionUID = 1L;
		private SpoutOutputCollector _collector;
		private Random _rand;
		private static final String[] sentences = new String[] 
				{ "the cow jumped over the moon", "an apple a day keeps the doctor away",
				"four score and seven years ago", "snow white and the seven dwarfs", 
				"i am at two with nature" };

		@Override
		public void open(@SuppressWarnings("rawtypes") Map conf, TopologyContext context, SpoutOutputCollector collector) {
			_collector = collector;
			_rand = new Random();
		}

		@Override
		public void nextTuple() {
			Utils.sleep(500);
			String sentence = sentences[_rand.nextInt(sentences.length)];
			_collector.emit(new Values(sentence));
		}

		@Override
		public void ack(Object id) {
		}

		@Override
		public void fail(Object id) {
		}

		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("sentence"));
		}

	}

	public static void main(String[] args) throws Exception, InvalidTopologyException {

		TopologyBuilder builder = new TopologyBuilder();

		builder.setSpout("spout", new StormRandomSentenceSpout(), 5);
		builder
			.setBolt("split", new SplitSentence(), 10)
				.shuffleGrouping("spout");
		
		builder
			.setBolt("count", new WordCount(), 10)
			.fieldsGrouping("split", new Fields("word"));

		Config conf = new Config();
		conf.setDebug(true);

		if (args != null && args.length > 0) {
			conf.setNumWorkers(3);

			StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
		} else {
			conf.setMaxTaskParallelism(3);

			LocalCluster cluster = new LocalCluster();
			cluster.submitTopology("word-count", conf, builder.createTopology());

			Thread.sleep(20000);

			cluster.shutdown();
		}

	}
}
