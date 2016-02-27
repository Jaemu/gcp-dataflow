/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.examples.complete.game;

import com.google.cloud.dataflow.examples.complete.game.GameActionInfo.KeyField;
import com.google.cloud.dataflow.examples.complete.game.utils.ExerciseOptions;
import com.google.cloud.dataflow.examples.complete.game.utils.Input;
import com.google.cloud.dataflow.examples.complete.game.utils.Output;
import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.runners.DataflowPipelineRunner;
import com.google.cloud.dataflow.sdk.transforms.Aggregator;
import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.transforms.MapElements;
import com.google.cloud.dataflow.sdk.transforms.Mean;
import com.google.cloud.dataflow.sdk.transforms.PTransform;
import com.google.cloud.dataflow.sdk.transforms.ParDo;
import com.google.cloud.dataflow.sdk.transforms.Sum;
import com.google.cloud.dataflow.sdk.transforms.Values;
import com.google.cloud.dataflow.sdk.transforms.View;
import com.google.cloud.dataflow.sdk.transforms.windowing.FixedWindows;
import com.google.cloud.dataflow.sdk.transforms.windowing.Window;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.dataflow.sdk.values.TypeDescriptor;

import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This is the final exercise in our series. We use side-inputs to build a more complicated
 * pipeline structure.
 *
 * <p> This pipeline builds on the {@link Exercise4} functionality, and adds some "business
 * intelligence" analysis: abuse detection. The pipeline derives the Mean user
 * score sum for a window, and uses that information to identify likely spammers/robots. (The robots
 * have a higher click rate than the human users). The 'robot' users are then filtered out when
 * calculating the team scores.
 */
public class Exercise5  {

  /**
   * Filter out all but those users with a high clickrate, which we will consider as 'spammy' uesrs.
   * We do this by finding the mean total score per user, then using that information as a side
   * input to filter out all but those user scores that are > (mean * SCORE_WEIGHT)
   */
  public static class CalculateSpammyUsers
      extends PTransform<PCollection<KV<String, Integer>>, PCollection<KV<String, Integer>>> {
    private static final Logger LOG = LoggerFactory.getLogger(CalculateSpammyUsers.class);
    private static final double SCORE_WEIGHT = 2.5;

    @Override
    public PCollection<KV<String, Integer>> apply(PCollection<KV<String, Integer>> userScores) {


      // Get the sum of scores for each user.
      PCollection<KV<String, Integer>> sumScores = userScores
          .apply("UserSum", Sum.<String>integersPerKey());

      // Extract the score from each element, and use it to find the global mean.
      final PCollectionView<Double> globalMeanScore = sumScores.apply(Values.<Integer>create())
          .apply(Mean.<Integer>globally().asSingletonView());

      // Filter the user sums using the global mean.
      return sumScores
          .apply(ParDo
              .named("ProcessAndFilter")
              // use the derived mean total score as a side input
              .withSideInputs(globalMeanScore)
              .of(new DoFn<KV<String, Integer>, KV<String, Integer>>() {
                private final Aggregator<Long, Long> numSpammerUsers =
                  createAggregator("SpammerUsers", new Sum.SumLongFn());
                @Override
                public void processElement(ProcessContext c) {
                  int score = c.element().getValue();
                  double globalMean = c.sideInput(globalMean);
                  // [START EXERCISE 5 - Part 1]
                  // JavaDoc: https://cloud.google.com/dataflow/java-sdk/JavaDoc
                  // Developer Docs: https://cloud.google.com/dataflow/model/par-do#side-inputs
                  //
                  // If the score is 2.5x the global average in each window, log the
                  // username and its score. Hint: (Use LOG.info for logging the score)
                  //
                  // Increment the numSpammerUsers aggregator to record the number of
                  // spammers identified.
                  //
                  // Output only the spammy user entries.
                  /* YOUR CODE GOES HERE */
                  // [END EXERCISE 5 - Part 1]
                }
              }));
    }
  }

  public static class WindowedNonSpammerTeamScore
      extends PTransform<PCollection<GameActionInfo>, PCollection<KV<String, Integer>>> {

    private Duration windowSize;

    public WindowedNonSpammerTeamScore(Duration windowSize) {
      this.windowSize = windowSize;
    }

    @Override
    public PCollection<KV<String, Integer>> apply(PCollection<GameActionInfo> input) {
      // Create a side input view of the spammy users
      final PCollectionView<Map<String, Integer>> spammersView = createSpammersView(input);

      return input
          .apply("TeamWindows", Window.<GameActionInfo>into(FixedWindows.of(windowSize)))
          // [START EXERCISE 5 - Part 2]
          // JavaDoc: https://cloud.google.com/dataflow/java-sdk/JavaDoc
          // Developer Docs: https://cloud.google.com/dataflow/model/par-do#side-inputs
          //
          // For this part, we'll use the previously computed spammy users as a side-input
          // to identify which entries should be filtered out.
          // Compute team scores over only those individuals who are not identified as
          // spammers.
          .apply("FilterOutSpammers", ParDo
              // Configure the ParDo to read the side input. Hint: use ParDo.withSideInputs()
              /* YOUR CODE GOES HERE */.
              .of(
                /* YOUR CODE GOES HERE */
              ))
          // Use the ExtractAndSumScore to compute the team scores.
          .apply("ExtractTeamScore", new ExtractAndSumScore(KeyField.TEAM));
          // [END EXERCISE 5 - Part 2]
    }

    private PCollectionView<Map<String, Integer>> createSpammersView(
        PCollection<GameActionInfo> input) {
      return input
          .apply("ExtractUserScore",
              MapElements.via((GameActionInfo gInfo) -> KV.of(gInfo.getUser(), gInfo.getScore()))
                .withOutputType(new TypeDescriptor<KV<String, Integer>>() {}))
          .apply("UserWindows", Window.<KV<String, Integer>>into(FixedWindows.of(windowSize)))
          // Filter out everyone but those with (SCORE_WEIGHT * avg) clickrate.
          // These might be robots/spammers.
          .apply("CalculateSpammyUsers", new CalculateSpammyUsers())
          // Derive a view from the collection of spammer users. It will be used as a side input
          // in calculating the team score sums, below.
          .apply("CreateSpammersView", View.<String, Integer>asMap());
    }
  }

  /**
   * A transform to extract key/score information from GameActionInfo, and sum the scores. The
   * constructor arg determines whether 'team' or 'user' info is extracted.
   */
  private static class ExtractAndSumScore
      extends PTransform<PCollection<GameActionInfo>, PCollection<KV<String, Integer>>> {

    private final KeyField field;

    ExtractAndSumScore(KeyField field) {
      this.field = field;
    }

    @Override
    public PCollection<KV<String, Integer>> apply(
        PCollection<GameActionInfo> gameInfo) {
      return gameInfo
        .apply(MapElements
            .via((GameActionInfo gInfo) -> KV.of(field.extract(gInfo), gInfo.getScore()))
            .withOutputType(new TypeDescriptor<KV<String, Integer>>() {}))
        .apply(Sum.<String>integersPerKey());
    }
  }

  private static final Duration WINDOW_SIZE = Duration.standardMinutes(5);


  public static void main(String[] args) throws Exception {
    ExerciseOptions options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(ExerciseOptions.class);
    // Enforce that this pipeline is always run in streaming mode.
    options.setStreaming(true);
    // Allow the pipeline to be cancelled automatically.
    options.setRunner(DataflowPipelineRunner.class);
    Pipeline pipeline = Pipeline.create(options);

    // Read Events from the custom unbounded source
    PCollection<GameActionInfo> rawEvents = pipeline.apply(new Input.UnboundedGenerator());

    // Calculate the total score per team over fixed windows,
    // and emit cumulative updates for late data. Uses the side input derived above-- the set of
    // suspected robots-- to filter out scores from those users from the sum.
    rawEvents
      .apply(new WindowedNonSpammerTeamScore(WINDOW_SIZE))
      // Write the result to BigQuery
      .apply(new Output.WriteTriggeredTeamScore());

    pipeline.run();
  }
}
